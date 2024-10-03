package com.example.mockservicebroker.servicebroker;

import java.util.Map;

import com.example.mockservicebroker.iam.IamService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.model.Role;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v2/service_instances/{instanceId}/service_bindings/{bindingId}")
public class ServiceBindingController {

	private final IamService iamService;

	private static final Logger log = LoggerFactory.getLogger(ServiceBindingController.class);

	public ServiceBindingController(IamService iamService) {
		this.iamService = iamService;
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#binding
	@PutMapping
	public ResponseEntity<ServiceBindResponse> bind(@PathVariable("instanceId") String instanceId,
			@PathVariable("bindingId") String bindingId, @RequestBody ServiceBindRequest request) {
		log.info("bind instanceId={}, bindingId={}, request={}", instanceId, bindingId, request);
		Role role = this.iamService.findRoleByInstanceId(instanceId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instance not found"));
		Map<String, Object> credentials = Map.of("role_name", role.roleName(), "role_arn", role.arn());
		return ResponseEntity.status(HttpStatus.CREATED).body(new ServiceBindResponse(credentials));
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#unbinding
	@DeleteMapping
	public ResponseEntity<Map<String, Object>> unbind(@PathVariable("instanceId") String instanceId,
			@PathVariable("bindingId") String bindingId) {
		log.info("unbind instanceId={}, bindingId={}", instanceId, bindingId);
		return ResponseEntity.ok(Map.of());
	}

	public record ServiceBindRequest(@JsonProperty("context") Map<String, Object> context,
			@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
			@JsonProperty("bind_resource") BindResource bindResource,
			@JsonProperty("parameters") Map<String, Object> parameters) {
	}

	public record ServiceBindResponse(Map<String, Object> credentials) {
	}

	public record BindResource(@JsonProperty("app_guid") String appGuid, @JsonProperty("route") String route) {
	}

}