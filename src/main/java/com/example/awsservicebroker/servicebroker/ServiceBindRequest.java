package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.lang.Nullable;

public record ServiceBindRequest(@Nullable @JsonProperty("context") Context context,
		@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
		@Nullable @JsonProperty("bind_resource") BindResource bindResource,
		@Nullable @JsonProperty("parameters") Map<String, Object> parameters) {

	@Nullable
	public <T> T bindParametersTo(Class<T> clazz, ObjectMapper objectMapper) {
		if (this.parameters == null) {
			return null;
		}
		return objectMapper.convertValue(this.parameters, clazz);
	}
}
