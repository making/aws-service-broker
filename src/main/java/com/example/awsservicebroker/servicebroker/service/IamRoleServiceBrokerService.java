package com.example.awsservicebroker.servicebroker.service;

import java.util.Map;

import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;
import software.amazon.awssdk.services.iam.model.Role;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static com.example.awsservicebroker.servicebroker.service.ServiceBrokerService.credentialsWithRole;

@Component
public class IamRoleServiceBrokerService implements ServiceBrokerService {

	private final IamService iamService;

	public IamRoleServiceBrokerService(IamService iamService) {
		this.iamService = iamService;
	}

	@Override
	public Map<String, Object> provisioning(String instanceId, ServiceProvisioningRequest request) {
		try {
			this.iamService.createIamRole(this.buildInstance(instanceId, request));
		}
		catch (EntityAlreadyExistsException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
		}
		return Map.of();
	}

	@Override
	public void deprovisioning(String instanceId, String serviceId, String planId) {
		this.iamService.deleteIamRoleByInstanceId(instanceId);
	}

	@Override
	public Map<String, Object> bind(String instanceId, String bindingId, ServiceBindRequest request) {
		Role role = this.iamService.findRoleByInstanceId(instanceId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instance not found"));
		return credentialsWithRole(role, Map.of());
	}

}
