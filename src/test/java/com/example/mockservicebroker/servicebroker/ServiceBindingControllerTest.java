package com.example.mockservicebroker.servicebroker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
class ServiceBindingControllerTest {

	RestClient restClient;

	@BeforeEach
	void setUp(@Autowired RestClient.Builder restClientBuilder, @LocalServerPort int port) {
		this.restClient = restClientBuilder.baseUrl("http://localhost:" + port)
			.defaultHeaders(httpHeaders -> httpHeaders.setBasicAuth("admin", "password"))
			.requestFactory(new JdkClientHttpRequestFactory())
			.build();
	}

	@Test
	void bind() {
		String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";
		String serviceId = "5edee818-720e-499e-bf10-55dfae43703b";
		String planId = "0ed05edb-7e48-4ad9-bded-8fe37638e2e3";
		String appGuid = "ee4a897f-a9ce-42c9-8318-4775d692836b";
		String bindingId = "0fdf05c7-c607-4ba1-89e8-1079ce1e08f5";
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry",
					    "some_field": "some-contextual-data"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					    "parameter1-name-here": 1,
					    "parameter2-name-here": "parameter2-value-here"
					  }
					}
					""".formatted(serviceId, planId, appGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("credentials")).isTrue();
		assertThat(body.get("credentials").get("username")).isEqualTo(new TextNode(bindingId));
		assertThat(body.get("credentials").has("password")).isTrue();
		assertThat(body.get("credentials").get("password").asText()).isNotEmpty();
		assertThat(body.get("credentials").get("database")).isEqualTo(new TextNode(instanceId));
	}

	@Test
	void unbind() {
		String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";
		String bindingId = "0fdf05c7-c607-4ba1-89e8-1079ce1e08f5";
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}