package com.example.mockservicebroker.servicebroker;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ServiceInstanceControllerTest {

	RestClient restClient;

	@BeforeEach
	void setUp(@Autowired RestClient.Builder restClientBuilder, @LocalServerPort int port) {
		this.restClient = restClientBuilder.baseUrl("http://localhost:" + port)
			.defaultHeaders(httpHeaders -> httpHeaders.setBasicAuth("admin", "password"))
			.requestFactory(new JdkClientHttpRequestFactory())
			.build();
	}

	@Test
	void provisioning() {
		String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";
		String serviceId = "5edee818-720e-499e-bf10-55dfae43703b";
		String planId = "0ed05edb-7e48-4ad9-bded-8fe37638e2e3";
		String organizationGuid = "353cca93-d0dc-4fa9-b973-588fb7c69cc8";
		String spaceGuid = "afe9189b-a989-4bce-9b11-13fc34317b6e";
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "service_id": "%s",
					  "plan_id": "%s",
					  "context": {
					    "platform": "cloudfoundry",
					    "some_field": "some-contextual-data"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "parameters": {
					    "parameter1": 1,
					    "parameter2": "foo"
					  },
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	void update() {
		String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";
		String serviceId = "5edee818-720e-499e-bf10-55dfae43703b";
		String planId = "0ed05edb-7e48-4ad9-bded-8fe37638e2e3";
		String organizationGuid = "353cca93-d0dc-4fa9-b973-588fb7c69cc8";
		String spaceGuid = "afe9189b-a989-4bce-9b11-13fc34317b6e";
		ResponseEntity<JsonNode> response = this.restClient.patch()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry",
					    "some_field": "some-contextual-data"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "parameters": {
					    "parameter1": 1,
					    "parameter2": "foo"
					  },
					  "previous_values": {
					    "plan_id": "%s",
					    "service_id": "%s",
					    "organization_id": "%s",
					    "space_id": "%s",
					    "maintenance_info": {
					      "version": "2.1.1+abcdef"
					    }
					  },
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, planId, serviceId, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void deprovisioning() {
		String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}