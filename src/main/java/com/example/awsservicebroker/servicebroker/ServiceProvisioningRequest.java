package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.example.awsservicebroker.servicebroker.api.ServiceInstanceController;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;

public record ServiceProvisioningRequest(@JsonProperty("service_id") String serviceId,
		@JsonProperty("plan_id") String planId, @Nullable @JsonProperty("context") Context context,
		@JsonProperty("organization_guid") String organizationGuid, @JsonProperty("space_guid") String spaceGuid,
		@Nullable @JsonProperty("parameters") Map<String, Object> parameters,
		@Nullable @JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
}
