package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.example.awsservicebroker.servicebroker.api.ServiceInstanceController;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.lang.Nullable;

public record ServiceUpdateRequest(@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
		@JsonProperty("parameters") Map<String, Object> parameters, @JsonProperty("context") Context context,
		@JsonProperty("previous_values") PreviousValues previousValues,
		@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {

	@Nullable
	public <T> T bindParametersTo(Class<T> clazz, ObjectMapper objectMapper) {
		if (this.parameters == null) {
			return null;
		}
		return objectMapper.convertValue(this.parameters, clazz);
	}
}
