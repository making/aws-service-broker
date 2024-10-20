package com.example.awsservicebroker.servicebroker.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.awsservicebroker.aws.Instance;
import com.example.awsservicebroker.servicebroker.Context;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import com.example.awsservicebroker.servicebroker.ServiceUpdateRequest;
import software.amazon.awssdk.services.iam.model.Role;

public interface ServiceBrokerService {

	default Instance buildInstance(String instanceId, ServiceProvisioningRequest request) {
		Context context = request.context();
		String instanceName = context == null ? "unknown-instance" : context.instanceName();
		String orgName = context == null ? "unknown-org" : context.organizationName();
		String spaceName = context == null ? "unknown-space" : context.spaceName();
		String orgGuid = context == null ? "unknown-org-guid" : context.organizationGuid();
		String spaceGuid = context == null ? "unknown-space-guid" : context.spaceGuid();
		return Instance.builder()
			.instanceId(instanceId)
			.instanceName(instanceName)
			.orgGuid(orgGuid)
			.orgName(orgName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build();
	}

	static String joinTagValue(String... values) {
		return String.join("|", values);
	}

	static String[] splitTagValue(String tagValue) {
		return tagValue.split("\\|");
	}

	default Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		return Map.of();
	}

	default Map<String, Object> update(String instanceId, ServiceUpdateRequest request) {
		return Map.of();
	}

	default void deprovisioning(String instanceId, String serviceId, String planId) {

	}

	default Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		return Map.of();
	}

	default void unbind(String instanceId, String bindingId, String serviceId, String planId) {

	}

	static Map<String, Object> credentialsWithRole(Role role, Map<String, Object> additionalCredentials) {
		Map<String, Object> credentials = new LinkedHashMap<>();
		credentials.put("role_name", role.roleName());
		credentials.put("role_arn", role.arn());
		credentials.putAll(additionalCredentials);
		return Collections.unmodifiableMap(credentials);
	}

}
