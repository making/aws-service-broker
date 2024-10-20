package com.example.awsservicebroker.servicebroker.service;

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
public class S3ServiceBrokerService extends AbstractServiceBrokerService {

	private final S3Service s3Service;

	private final ObjectMapper objectMapper;

	public S3ServiceBrokerService(S3Service s3Service, IamService iamService, ObjectMapper objectMapper) {
		super(iamService);
		this.s3Service = s3Service;
		this.objectMapper = objectMapper;
	}

	@Override
	protected AwsService awsService() {
		return AwsService.S3;
	}

	@Override
	public Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		ProvisioningParameters params = request.bindParametersTo(ProvisioningParameters.class, this.objectMapper);
		if (params == null || !StringUtils.hasText(params.roleName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'role_name' parameter is required");
		}
		String roleName = params.roleName();
		super.addRoleTag(roleName, instanceId, () -> {
			String bucketNameToCreate = params.bucketName();
			String regionNameToCreate = params.region();
			CreateBucketResult result = this.s3Service.createBucket(this.buildInstance(instanceId, request),
					bucketNameToCreate, regionNameToCreate);
			if (params.enableVersioning()) {
				this.s3Service.enableVersioning(result.bucketName());
			}
			return joinTagValue(result.bucketName(), result.region());
		});
		return Map.of();
	}

	@Override
	public Map<String, Object> update(String instanceId, ServiceUpdateRequest request) {
		UpdatingParameters params = request.bindParametersTo(UpdatingParameters.class, this.objectMapper);
		if (params != null) {
			RoleAndRoleTagValue roleAndRoleTagValue = super.findRoleAndRoleTagValue(instanceId);
			String[] tagValue = splitTagValue(roleAndRoleTagValue.roleTagValue());
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
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		RoleAndResult<String[]> roleAndResult = super.attachInlinePolicy(instanceId, bindingId, roleTagValue -> {
			String[] tagValue = splitTagValue(roleTagValue);
			String bucketName = tagValue[0];
			String region = tagValue[1];
			String policy = this.s3Service.buildTrustPolicyForBucket(bucketName);
			return new PolicyAndResult<>(policy, new String[] { bucketName, region });
		});
		Role role = roleAndResult.role();
		String bucketName = roleAndResult.result()[0];
		String region = roleAndResult.result()[1];
		return Map.of("role_arn", role.arn(), "role_name", role.roleName(), "bucket_name", bucketName, "region",
				region);
	}

	@Override
	public void unbind(String instanceId, String bindingId, String serviceId, String planId) {
		super.detachInlinePolicy(instanceId, bindingId, () -> {

		});
	}

	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		super.removeRoleTag(instanceId, roleTagValue -> {
			if (StringUtils.hasText(roleTagValue)) {
				String bucketName = splitTagValue(roleTagValue)[0];
				this.s3Service.deleteBucket(bucketName);
			}
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
