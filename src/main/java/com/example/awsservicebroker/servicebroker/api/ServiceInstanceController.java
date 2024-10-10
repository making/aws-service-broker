package com.example.awsservicebroker.servicebroker.api;

import java.util.Map;

import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.servicebroker.ServiceProvisioningRequest;
import com.example.awsservicebroker.servicebroker.ServiceUpdateRequest;
import com.example.awsservicebroker.servicebroker.service.ServiceBrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v2/service_instances/{instanceId}")
public class ServiceInstanceController {

	private final Map<String, ServiceBrokerService> serviceBrokerServices;

	private static final Logger log = LoggerFactory.getLogger(ServiceInstanceController.class);

	public ServiceInstanceController(Map<String, ServiceBrokerService> serviceBrokerServices) {
		this.serviceBrokerServices = serviceBrokerServices;
	}

	ServiceBrokerService getServiceBrokerService(String serviceId) {
		AwsService service = AwsService.fromServiceId(serviceId);
		if (service == null || !this.serviceBrokerServices.containsKey(service.getServiceBrokerServiceBeanName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unsupported service id: %s".formatted(serviceId));
		}
		return this.serviceBrokerServices.get(service.getServiceBrokerServiceBeanName());
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#provisioning
	@PutMapping
	public ResponseEntity<Map<String, Object>> provisioning(@PathVariable("instanceId") String instanceId,
			@RequestBody ServiceProvisioningRequest request) {
		log.info("Provisioning instanceId={} request={}", instanceId, request);
		ServiceBrokerService serviceBrokerService = this.getServiceBrokerService(request.serviceId());
		Map<String, Object> provisioned = serviceBrokerService.provisioning(instanceId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(provisioned);
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#updating-a-service-instance
	@PatchMapping
	public ResponseEntity<Map<String, Object>> update(@PathVariable("instanceId") String instanceId,
			@RequestBody ServiceUpdateRequest request) {
		log.info("Update instanceId={} request={}", instanceId, request);
		ServiceBrokerService serviceBrokerService = this.getServiceBrokerService(request.serviceId());
		serviceBrokerService.update(instanceId, request);
		// log.warn("This operation is not supported");
		return ResponseEntity.ok(Map.of());
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#deprovisioning
	@DeleteMapping
	public ResponseEntity<?> deprovisioning(@PathVariable("instanceId") String instanceId,
			@RequestParam("service_id") String serviceId, @RequestParam("plan_id") String planId) {
		log.info("Deprovisioning instanceId={} serviceId={} planId={}", instanceId, serviceId, planId);
		ServiceBrokerService serviceBrokerService = this.getServiceBrokerService(serviceId);
		serviceBrokerService.deprovisioning(instanceId, serviceId, planId);
		return ResponseEntity.ok(Map.of());
	}

}