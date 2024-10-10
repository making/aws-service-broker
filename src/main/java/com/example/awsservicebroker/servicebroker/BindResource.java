package com.example.awsservicebroker.servicebroker;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BindResource(@JsonProperty("app_guid") String appGuid, @JsonProperty("route") String route) {
}
