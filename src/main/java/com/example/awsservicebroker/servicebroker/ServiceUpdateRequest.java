package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.example.awsservicebroker.servicebroker.api.ServiceInstanceController;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceUpdateRequest(@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
		@JsonProperty("parameters") Map<String, Object> parameters, @JsonProperty("context") Context context,
		@JsonProperty("previous_values") PreviousValues previousValues,
		@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
}
