package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.example.awsservicebroker.iam.IamService;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v2/service_instances/{instanceId}")
public class ServiceInstanceController {

	private final IamService iamService;

	private static final Logger log = LoggerFactory.getLogger(ServiceInstanceController.class);

	public ServiceInstanceController(IamService iamService) {
		this.iamService = iamService;
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#provisioning
	@PutMapping
	public ResponseEntity<Map<String, Object>> provisioning(@PathVariable("instanceId") String instanceId,
			@RequestBody ServiceProvisioningRequest request) {
		log.info("Provisioning instanceId={} request={}", instanceId, request);
		Context context = request.context();
		String orgName = context.organizationName();
		String spaceName = context.spaceName();
		this.iamService.findRoleByOrgNameAndSpaceName(orgName, spaceName).ifPresent(__ -> {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The IAM role for the given org and space already exists. You can only create one IAM role per org and space.");
		});
		this.iamService.createIamRole(instanceId, context.organizationGuid(), context.spaceGuid(), orgName, spaceName);
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of());
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#updating-a-service-instance
	@PatchMapping
	public ResponseEntity<Map<String, Object>> update(@PathVariable("instanceId") String instanceId,
			@RequestBody ServiceUpdateRequest request) {
		log.info("Update instanceId={} request={}", instanceId, request);
		log.warn("This operation is not supported");
		return ResponseEntity.ok(Map.of());
	}

	// https://github.com/openservicebrokerapi/servicebroker/blob/v2.15/spec.md#deprovisioning
	@DeleteMapping
	public ResponseEntity<?> deprovisioning(@PathVariable("instanceId") String instanceId) {
		log.info("Deprovisioning instanceId={}", instanceId);
		this.iamService.deleteIamRoleByInstanceId(instanceId);
		return ResponseEntity.ok(Map.of());
	}

	public record ServiceProvisioningRequest(@JsonProperty("service_id") String serviceId,
			@JsonProperty("plan_id") String planId, @JsonProperty("context") Context context,
			@JsonProperty("organization_guid") String organizationGuid, @JsonProperty("space_guid") String spaceGuid,
			@JsonProperty("parameters") Map<String, Object> parameters,
			@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
	}

	public record Context(@JsonProperty("organization_guid") String organizationGuid,
			@JsonProperty("space_guid") String spaceGuid, @JsonProperty("organization_name") String organizationName,
			@JsonProperty("space_name") String spaceName) {
	}

	public record MaintenanceInfo(@JsonProperty("version") String version) {
	}

	public record ServiceUpdateRequest(@JsonProperty("service_id") String serviceId,
			@JsonProperty("plan_id") String planId, @JsonProperty("parameters") Map<String, Object> parameters,
			@JsonProperty("context") Context context, @JsonProperty("previous_values") PreviousValues previousValues,
			@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
	}

	public record PreviousValues(@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
			@JsonProperty("organization_id") String organizationId, @JsonProperty("space_id") String spaceId,
			@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
	}

}