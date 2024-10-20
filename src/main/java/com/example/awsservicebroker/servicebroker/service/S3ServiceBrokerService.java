package com.example.awsservicebroker.servicebroker.service;

import java.util.List;
import java.util.Map;

import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.aws.s3.S3Service;
import com.example.awsservicebroker.aws.s3.S3Service.CreateBucketResult;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import com.example.awsservicebroker.servicebroker.ServiceUpdateRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.iam.model.Role;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static com.example.awsservicebroker.servicebroker.service.ServiceBrokerService.joinTagValue;
import static com.example.awsservicebroker.servicebroker.service.ServiceBrokerService.splitTagValue;

@Component
public class S3ServiceBrokerService implements ServiceBrokerService {

	private final S3Service s3Service;

	private final IamService iamService;

	private final ObjectMapper objectMapper;

	public S3ServiceBrokerService(S3Service s3Service, IamService iamService, ObjectMapper objectMapper) {
		this.s3Service = s3Service;
		this.iamService = iamService;
		this.objectMapper = objectMapper;
	}

	@Override
	public Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		ProvisioningParameters params = request.bindParametersTo(ProvisioningParameters.class, this.objectMapper);
		if (params == null || !StringUtils.hasText(params.roleName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'role_name' parameter is required");
		}
		String roleName = params.roleName();
		this.iamService.findRoleByRoleName(roleName)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The given role (role_name=%s) is not found".formatted(roleName)));
		String bucketNameToCreate = params.bucketName();
		String regionNameToCreate = params.region();
		CreateBucketResult result = this.s3Service.createBucket(this.buildInstance(instanceId, request),
				bucketNameToCreate, regionNameToCreate);
		if (params.enableVersioning()) {
			this.s3Service.enableVersioning(result.bucketName());
		}
		String roleTagKey = AwsService.S3.roleTagKey(instanceId);
		this.iamService.addRoleTags(roleName,
				List.of(software.amazon.awssdk.services.iam.model.Tag.builder()
					.key(roleTagKey)
					.value(joinTagValue(result.bucketName(), result.region()))
					.build()));
		return Map.of();
	}

	@Override
	public Map<String, Object> update(String instanceId, ServiceUpdateRequest request) {
		UpdatingParameters params = request.bindParametersTo(UpdatingParameters.class, this.objectMapper);
		if (params != null) {
			String roleTagKey = AwsService.S3.roleTagKey(instanceId);
			Role role = this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "The instance has gone."));
			List<software.amazon.awssdk.services.iam.model.Tag> tags = this.iamService.listRoleTags(role.roleName());
			software.amazon.awssdk.services.iam.model.Tag roleTag = tags.stream()
				.filter(tag -> tag.key().equals(roleTagKey))
				.findAny()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
						"The corresponding tag '%s' is not found in the role '%s'.".formatted(roleTagKey,
								role.roleName())));
			String[] tagValue = splitTagValue(roleTag.value());
			String bucketName = tagValue[0];
			if (params.enableVersioning()) {
				this.s3Service.enableVersioning(bucketName);
			}
			else {
				this.s3Service.suspendVersioning(bucketName);
			}
		}
		return Map.of();
	}

	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		String roleTagKey = AwsService.S3.roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey)).ifPresent(role -> {
			List<software.amazon.awssdk.services.iam.model.Tag> tags = this.iamService.listRoleTags(role.roleName());
			tags.stream().filter(tag -> tag.key().equals(roleTagKey)).findAny().ifPresent(tag -> {
				String value = tag.value();
				if (StringUtils.hasText(value)) {
					String bucketName = splitTagValue(value)[0];
					this.s3Service.deleteBucket(bucketName);
				}
			});
			this.iamService.removeRoleTags(role.roleName(), List.of(roleTagKey));
		});
	}

	@Override
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		String roleTagKey = AwsService.S3.roleTagKey(instanceId);
		Role role = this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "Instance has gone"));
		List<software.amazon.awssdk.services.iam.model.Tag> tags = this.iamService.listRoleTags(role.roleName());
		software.amazon.awssdk.services.iam.model.Tag roleTag = tags.stream()
			.filter(tag -> tag.key().equals(roleTagKey))
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
					"The corresponding tag '%s' is not found in the role '%s'.".formatted(roleTagKey,
							role.roleName())));
		String[] tagValue = splitTagValue(roleTag.value());
		String bucketName = tagValue[0];
		String region = tagValue[1];
		String policyName = AwsService.S3.policyName(instanceId, bindingId);
		String policy = this.s3Service.buildTrustPolicyForBucket(bucketName);
		this.iamService.attachInlinePolicyToRole(role.roleName(), policyName, policy);
		return Map.of("role_arn", role.arn(), "role_name", role.roleName(), "bucket_name", bucketName, "region",
				region);
	}

	@Override
	public void unbind(String instanceId, String bindingId, String serviceId, String planId) {
		String roleTagKey = AwsService.S3.roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey)).ifPresent(role -> {
			String policyName = AwsService.S3.policyName(instanceId, bindingId);
			this.iamService.detachInlinePolicyFromRole(role.roleName(), policyName);
		});
	}

	public record ProvisioningParameters(@Nullable @JsonProperty("role_name") String roleName,
			@Nullable @JsonProperty("bucket_name") String bucketName,
			@Nullable @JsonProperty("enable_versioning") boolean enableVersioning,
			@Nullable @JsonProperty("region") String region) {
	}

	public record UpdatingParameters(@Nullable @JsonProperty("enable_versioning") boolean enableVersioning) {
	}

}
