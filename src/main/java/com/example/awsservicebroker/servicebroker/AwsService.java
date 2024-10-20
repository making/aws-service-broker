package com.example.awsservicebroker.servicebroker;

import java.util.List;
import java.util.Locale;

import com.example.awsservicebroker.utils.StringUtils;

import org.springframework.lang.Nullable;

public enum AwsService {

	IAM_ROLE("5edee818-720e-499e-bf10-55dfae43703b",
			List.of(new SimplePlan("free", "0ed05edb-7e48-4ad9-bded-8fe37638e2e3"))),
	S3("2de9054b-fad9-4b25-84c9-efe432b14c2f", List.of(new SimplePlan("free", "a42e2c0c-64e0-41c5-a59c-be52c592812e"))),
	DYNAMODB("0675f97b-029d-4ab9-b5aa-53b6602cc53c",
			List.of(new SimplePlan("iam-only", "d3094f5c-dc91-4617-9bd6-e6b52f920148")));

	private final String serviceId;

	private final List<Plan> plans;

	AwsService(String serviceId, List<Plan> plans) {
		this.serviceId = serviceId;
		this.plans = plans;
	}

	public String serviceId() {
		return serviceId;
	}

	public List<Plan> plans() {
		return plans;
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

	public String policyName(String instanceId, String bindingId) {
		return name().toLowerCase(Locale.ENGLISH) + "-" + StringUtils.removeHyphen(instanceId) + "-" + StringUtils.removeHyphen(bindingId);
	}

	public String getServiceBrokerServiceBeanName() {
		return StringUtils.toUpperCamel(name()) + "ServiceBrokerService";
	}

	public interface Plan {

		String planName();

		String planId();

	}

	record SimplePlan(String planName, String planId) implements Plan {

	}

}
