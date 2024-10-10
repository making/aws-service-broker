package com.example.awsservicebroker.servicebroker;

import java.util.Map;

public record ServiceBindResponse(Map<String, Object> credentials) {
}
