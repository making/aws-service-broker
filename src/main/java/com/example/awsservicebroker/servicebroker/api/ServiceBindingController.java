package com.example.awsservicebroker.servicebroker.api;

import java.util.Map;

import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.aws.s3.S3Service;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.servicebroker.Context;
import com.example.awsservicebroker.servicebroker.ServiceBindRequest;
import com.example.awsservicebroker.servicebroker.ServiceBindResponse;
import com.example.awsservicebroker.servicebroker.service.ServiceBrokerService;
import com.example.awsservicebroker.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.s3.model.Bucket;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v2/service_instances/{instanceId}/service_bindings/{bindingId}")
public class ServiceBindingController {

	private final Map<String, ServiceBrokerService> serviceBrokerServices;

	private final IamService iamService;

	private final S3Service s3Service;

	private final Region region;

	private static final Logger log = LoggerFactory.getLogger(ServiceBindingController.class);

	public ServiceBindingController(Map<String, ServiceBrokerService> serviceBrokerServices, IamService iamService,
			S3Service s3Service, AwsRegionProvider regionProvider) {
		this.serviceBrokerServices = serviceBrokerServices;
		this.iamService = iamService;
		this.s3Service = s3Service;
		this.region = regionProvider.getRegion();
	}

	ServiceBrokerService getServiceBrokerService(String serviceId) {
		AwsService service = AwsService.fromServiceId(serviceId);
		if (service == null || !this.serviceBrokerServices.containsKey(service.getServiceBrokerServiceBeanName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unsupported service id: %s".formatted(serviceId));
		}
		return this.serviceBrokerServices.get(service.getServiceBrokerServiceBeanName());
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#binding
	@PutMapping
	public ResponseEntity<ServiceBindResponse> bind(@PathVariable("instanceId") String instanceId,
			@PathVariable("bindingId") String bindingId, @RequestBody ServiceBindRequest request) {
		log.info("bind instanceId={}, bindingId={}, request={}", instanceId, bindingId, request);
		ServiceBrokerService serviceBrokerService = this.getServiceBrokerService(request.serviceId());
		Map<String, Object> credentials = serviceBrokerService.bind(instanceId, bindingId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ServiceBindResponse(credentials));
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#unbinding
	@DeleteMapping
	public ResponseEntity<Map<String, Object>> unbind(@PathVariable("instanceId") String instanceId,
			@PathVariable("bindingId") String bindingId, @RequestParam("service_id") String serviceId,
			@RequestParam("plan_id") String planId) {
		log.info("unbind instanceId={}, bindingId={}, serviceId={}, planId={}", instanceId, bindingId, serviceId,
				planId);
		ServiceBrokerService serviceBrokerService = this.getServiceBrokerService(serviceId);
		serviceBrokerService.unbind(instanceId, bindingId, serviceId, planId);
		return ResponseEntity.ok(Map.of());
	}

}