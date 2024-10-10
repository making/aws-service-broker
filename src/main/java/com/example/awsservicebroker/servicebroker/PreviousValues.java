package com.example.awsservicebroker.servicebroker;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreviousValues(@JsonProperty("service_id") String serviceId, @JsonProperty("plan_id") String planId,
		@JsonProperty("organization_id") String organizationId, @JsonProperty("space_id") String spaceId,
		@JsonProperty("maintenance_info") MaintenanceInfo maintenanceInfo) {
}
