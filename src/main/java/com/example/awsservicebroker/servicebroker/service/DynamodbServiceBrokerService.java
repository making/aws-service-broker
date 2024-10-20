package com.example.awsservicebroker.servicebroker.service;

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

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static com.example.awsservicebroker.servicebroker.service.ServiceBrokerService.credentialsWithRole;

@Component
public class DynamodbServiceBrokerService extends AbstractServiceBrokerService {

	private final DynamodbService dynamodbService;

	private final Region region;

	private final ObjectMapper objectMapper;

	public DynamodbServiceBrokerService(DynamodbService dynamodbService, IamService iamService,
			AwsRegionProvider regionProvider, ObjectMapper objectMapper) {
		super(iamService);
		this.dynamodbService = dynamodbService;
		this.region = regionProvider.getRegion();
		this.objectMapper = objectMapper;
	}

	@Override
	protected AwsService awsService() {
		return AwsService.DYNAMODB;
	}

	@Override
	public Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		ProvisioningParameters params = request.bindParametersTo(ProvisioningParameters.class, this.objectMapper);
		if (params == null || !StringUtils.hasText(params.roleName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'role_name' parameter is required");
		}
		String roleName = params.roleName();
		super.addRoleTag(roleName, instanceId, () -> this.dynamodbService.defaultTablePrefix(instanceId));
		return Map.of();
	}

	@Override
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		RoleAndResult<String> roleAndResult = super.attachInlinePolicy(instanceId, bindingId, tablePrefix -> {
			String policy = this.dynamodbService.buildTrustPolicyForTable(tablePrefix);
			return new PolicyAndResult<>(policy, tablePrefix);
		});
		Role role = roleAndResult.role();
		String tablePrefix = roleAndResult.result();
		return credentialsWithRole(role, Map.of("prefix", tablePrefix, "region", region.id()));
	}

	@Override
	public void unbind(String instanceId, String bindingId, String serviceId, String planId) {
		super.detachInlinePolicy(instanceId, bindingId, () -> {

		});
	}

	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		super.removeRoleTag(instanceId, roleTagValue -> {

		});
	}

	public record ProvisioningParameters(@Nullable @JsonProperty("role_name") String roleName) {
	}

}
