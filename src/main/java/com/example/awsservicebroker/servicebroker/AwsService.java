package com.example.awsservicebroker.servicebroker;

import com.example.awsservicebroker.utils.StringUtils;

import org.springframework.lang.Nullable;

public enum AwsService {

	IAM_ROLE("5edee818-720e-499e-bf10-55dfae43703b"), S3("2de9054b-fad9-4b25-84c9-efe432b14c2f");

	private final String serviceId;

	AwsService(String serviceId) {
		this.serviceId = serviceId;
	}

	public String serviceId() {
		return serviceId;
	}

	@Nullable
	public static AwsService fromServiceId(String serviceId) {
		for (AwsService service : AwsService.values()) {
			if (service.serviceId.equals(serviceId)) {
				return service;
			}
		}
		return null;
	}

	public String getServiceBrokerServiceBeanName() {
		return StringUtils.toUpperCamel(name()) + "ServiceBrokerService";
	}

}
