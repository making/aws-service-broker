package com.example.awsservicebroker.servicebroker.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.aws.s3.S3Service;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import com.example.awsservicebroker.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class S3ServiceBrokerService implements ServiceBrokerService {

	private final S3Service s3Service;

	private final IamService iamService;

	private final Region region;

	private final ObjectMapper objectMapper;

	public static final String ROLE_NAME_TAG_KEY = "role_name";

	public static final String POLICY_NAME_TAG_KEY = "policy_name";

	public S3ServiceBrokerService(S3Service s3Service, IamService iamService, AwsRegionProvider regionProvider,
			ObjectMapper objectMapper) {
		this.s3Service = s3Service;
		this.iamService = iamService;
		this.region = regionProvider.getRegion();
		this.objectMapper = objectMapper;
	}

	String keySuffix(@Nullable String bindingId) {
		return bindingId == null ? "" : "_" + StringUtils.removeHyphen(bindingId);
	}

	void attachPolicyAndPutBucketTags(String bucketName, String roleName, @Nullable String bindingId) {
		Role role = this.iamService.findRoleByRoleName(roleName)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
					"Role with name %s does not exist.".formatted(roleName)));
		String policyDocument = this.s3Service.buildTrustPolicyForBucket(bucketName);
		String policyName = "s3-" + bucketName + (bindingId == null ? "" : "-" + StringUtils.removeHyphen(bindingId));
		String keySuffix = keySuffix(bindingId);
		this.iamService.attachInlinePolicyToRole(role.roleName(), policyName, policyDocument);
		this.s3Service.putBucketTags(bucketName,
				List.of(Tag.builder().key(ROLE_NAME_TAG_KEY + keySuffix).value(roleName).build(),
						Tag.builder().key(POLICY_NAME_TAG_KEY + keySuffix).value(policyName).build()),
				true);
	}

	void detachPolicyAndRemoveBucketTags(String bucketName, String roleName, String policyName,
			@Nullable String bindingId) {
		String keySuffix = keySuffix(bindingId);
		this.iamService.detachInlinePolicyFromRole(roleName, policyName);
		this.s3Service.removeBucketTags(bucketName,
				List.of(Tag.builder().key(ROLE_NAME_TAG_KEY + keySuffix).value(roleName).build(),
						Tag.builder().key(POLICY_NAME_TAG_KEY + keySuffix).value(policyName).build()));
	}

	/**
	 * If the <code>role_name</code> parameter is provided, attaches a policy to access
	 * the specified bucket. Sets tags on the bucket with key: <code>role_name</code> and
	 * value: the value of the <code>role_name</code> parameter, and key:
	 * <code>policy_name</code> with value: <code>s3-{bucket_name}</code>.<br>
	 * If the <code>bucket_name</code> parameter is provided, use it as the bucket name to
	 * create.
	 * @param instanceId the ID of the service instance to provision
	 * @param request the service provisioning request
	 * @return a map of provisioning results (currently an empty map)
	 */
	@Override
	public Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		ProvisioningParameters params = request.bindParametersTo(ProvisioningParameters.class, this.objectMapper);
		String bucketNameToCreate = params == null ? null : params.bucketName();
		String bucketName = this.s3Service.createBucket(this.buildInstance(instanceId, request), bucketNameToCreate);
		if (params != null) {
			if (params.roleName() != null) {
				this.attachPolicyAndPutBucketTags(bucketName, params.roleName(), null);
			}
			if (params.enableVersioning()) {
				this.s3Service.enableVersioning(bucketName);
			}
		}
		return Map.of();
	}

	/**
	 * Retrieves roles set as values of tags with keys <code>role_name</code> and
	 * <code>role_name_{binding_id with hyphens removed}</code> on the specified bucket,
	 * and detaches policies set as values of tags with keys <code>policy_name</code> and
	 * <code>policy_name_{binding_id with hyphens removed}</code>.
	 * @param instanceId the ID of the service instance to deprovision
	 * @param serviceId the ID of the service associated with the instance
	 * @param planId the ID of the plan associated with the instance
	 */
	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		this.s3Service.findBucketByInstanceId(instanceId).ifPresent(bucket -> {
			String bucketName = bucket.name();
			Map<String, String> tagMap = this.s3Service.listBucketTags(bucketName)
				.stream()
				.collect(Collectors.toMap(Tag::key, Tag::value));
			tagMap.forEach((key, value) -> {
				if (key.equals(ROLE_NAME_TAG_KEY)) {
					String policyName = tagMap.get(POLICY_NAME_TAG_KEY);
					this.iamService.detachInlinePolicyFromRole(value, policyName);
				}
				else if (key.startsWith(ROLE_NAME_TAG_KEY)) {
					String keySuffix = key.substring(ROLE_NAME_TAG_KEY.length());
					String policyName = tagMap.get(POLICY_NAME_TAG_KEY + keySuffix);
					this.iamService.detachInlinePolicyFromRole(value, policyName);
				}
			});
			this.s3Service.deleteBucket(bucketName);
		});
	}

	/**
	 * If the <code>role_name</code> parameter is provided, attaches a policy to allow
	 * access to the specified bucket. Sets tags on the bucket with key:
	 * <code>role_name_{binding_id with hyphens removed}</code> and value: the
	 * <code>role_name</code> parameter's value, and with key:
	 * <code>policy_name_{binding_id with hyphens removed}</code> and value:
	 * <code>s3-{bucket_name}-{binding_id with hyphens removed}</code>. If the
	 * <code>role_name</code> parameter is not provided and no tag with key:
	 * <code>role_name</code> exists on the bucket, returns a 400 error.
	 * @param instanceId the ID of the instance to bind
	 * @param bindingId the ID of the binding for the specified instance
	 * @param request the service bind request
	 * @return a map containing bucket details
	 */
	@Override
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		Bucket bucket = this.s3Service.findBucketByInstanceId(instanceId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instance not found"));
		String bucketName = bucket.name();
		BindingParameters params = request.bindParametersTo(BindingParameters.class, this.objectMapper);
		if (params != null && params.roleName() != null) {
			String roleName = params.roleName();
			this.attachPolicyAndPutBucketTags(bucketName, roleName, bindingId);
		}
		else if (this.s3Service.listBucketTags(bucketName)
			.stream()
			.noneMatch(tag -> tag.key().equals(ROLE_NAME_TAG_KEY))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"If you do not specify the 'role_name' parameter in the service instance, you must specify the 'role_name' parameter in the service binding.");
		}
		return Map.of("bucket_name", bucket.name(), "region", this.region.id());
	}

	/**
	 * Retrieves roles set as values of tags with keys <code>role_name</code> and
	 * <code>role_name_{binding_id with hyphens removed}</code> on the specified bucket,
	 * and detaches policies set as values of tags with keys <code>policy_name</code> and
	 * <code>policy_name_{binding_id with hyphens removed}</code>.
	 * @param instanceId the ID of the instance associated with the bucket
	 * @param bindingId the ID of the binding to unbind from the instance
	 * @param serviceId the ID of the service associated with the instance
	 * @param planId the ID of the plan associated with the instance
	 */
	@Override
	public void unbind(String instanceId, String bindingId, String serviceId, String planId) {
		this.s3Service.findBucketByInstanceId(instanceId).ifPresent(bucket -> {
			String bucketName = bucket.name();
			String keySuffix = keySuffix(bindingId);
			String roleKey = ROLE_NAME_TAG_KEY + keySuffix;
			String policyKey = POLICY_NAME_TAG_KEY + keySuffix;
			Map<String, String> tagMap = this.s3Service.listBucketTags(bucketName)
				.stream()
				.collect(Collectors.toMap(Tag::key, Tag::value));
			if (tagMap.containsKey(roleKey)) {
				String roleName = tagMap.get(roleKey);
				String policyName = tagMap.get(policyKey);
				this.detachPolicyAndRemoveBucketTags(bucketName, roleName, policyName, bindingId);
			}
		});
	}

	public record ProvisioningParameters(@Nullable @JsonProperty("role_name") String roleName,
			@Nullable @JsonProperty("bucket_name") String bucketName,
			@Nullable @JsonProperty("enable_versioning") boolean enableVersioning) {
	}

	public record BindingParameters(@Nullable @JsonProperty("role_name") String roleName) {
	}

}
