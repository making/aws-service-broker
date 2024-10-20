package com.example.awsservicebroker.servicebroker.service;

import java.util.List;
import java.util.Map;

import com.example.awsservicebroker.aws.dynamodb.DynamodbService;
import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DynamodbServiceBrokerService implements ServiceBrokerService {
	private final DynamodbService dynamodbService;

	private final IamService iamService;

	private final Region region;

	private final ObjectMapper objectMapper;

	public DynamodbServiceBrokerService(DynamodbService dynamodbService, IamService iamService,
			AwsRegionProvider regionProvider, ObjectMapper objectMapper) {
		this.dynamodbService = dynamodbService;
		this.iamService = iamService;
		this.region = regionProvider.getRegion();
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
		String roleTagKey = AwsService.DYNAMODB.roleTagKey(instanceId);
		String tablePrefix = this.dynamodbService.defaultTablePrefix(instanceId);
		this.iamService.addRoleTags(roleName, List.of(Tag.builder().key(roleTagKey).value(tablePrefix).build()));
		return Map.of();
	}

	@Override
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		String policyName = AwsService.DYNAMODB.policyName(instanceId, bindingId);
		String roleTagKey = AwsService.DYNAMODB.roleTagKey(instanceId);
		Role role = this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
					"Role tag (key: %s) is missing".formatted(roleTagKey)));
		String tablePrefix = this.iamService.listRoleTags(role.roleName())
			.stream()
			.filter(tuple -> tuple.key().equals(roleTagKey))
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
					"Role tag (key: %s) is missing".formatted(roleTagKey)))
			.value();
		String policy = this.dynamodbService.buildTrustPolicyForTable(tablePrefix);
		this.iamService.attachInlinePolicyToRole(role.roleName(), policyName, policy);
		return Map.of("role_arn", role.arn(), "role_name", role.roleName(), "prefix", tablePrefix, "region",
				region.id());
	}

	@Override
	public void unbind(String instanceId, String bindingId, String serviceId, String planId) {
		String policyName = AwsService.DYNAMODB.policyName(instanceId, bindingId);
		String roleTagKey = AwsService.DYNAMODB.roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
			.ifPresent(role -> this.iamService.detachInlinePolicyFromRole(role.roleName(), policyName));
	}

	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		String roleTagKey = AwsService.DYNAMODB.roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
			.ifPresent(role -> this.iamService.removeRoleTags(role.roleName(), List.of(roleTagKey)));
	}

	public record ProvisioningParameters(@Nullable @JsonProperty("role_name") String roleName) {
	}

}
