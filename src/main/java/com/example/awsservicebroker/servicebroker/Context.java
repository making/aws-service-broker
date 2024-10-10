package com.example.awsservicebroker.servicebroker;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;

public record Context(@Nullable @JsonProperty("organization_guid") String organizationGuid,
		@Nullable @JsonProperty("space_guid") String spaceGuid,
		@Nullable @JsonProperty("organization_name") String organizationName,
		@Nullable @JsonProperty("space_name") String spaceName,
		@Nullable @JsonProperty("instance_name") String instanceName,
		@Nullable @JsonProperty("organization_annotations") Map<String, Object> organizationAnnotations,
		@Nullable @JsonProperty("space_annotations") Map<String, Object> spaceAnnotations,
		@Nullable @JsonProperty("instance_annotations") Map<String, Object> instanceAnnotations) {
}
