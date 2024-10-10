package com.example.awsservicebroker.servicebroker;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MaintenanceInfo(@JsonProperty("version") String version) {
}
