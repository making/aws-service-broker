package com.example.awsservicebroker.servicebroker;

import java.util.UUID;

import com.example.awsservicebroker.config.TestConfig;
import com.example.awsservicebroker.iam.IamService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "iam.oidc-provider-arn=arn:aws:iam::123456789012:oidc-provider/example.com" })
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("testcontainers")
@Import(TestConfig.class)
class ServiceInstanceControllerTest {

	RestClient restClient;

	@Autowired
	IamService iamService;

	String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";

	String serviceId = "5edee818-720e-499e-bf10-55dfae43703b";

	String planId = "0ed05edb-7e48-4ad9-bded-8fe37638e2e3";

	String organizationGuid = "4b84793c-f3ea-4a55-92b7-942726aac163";

	String spaceGuid = "34e1bb23-0e76-4aad-95d7-1abe3ea1dcd8";

	String organizationName = "demo";

	String spaceName = "test";

	@BeforeEach
	void setUp(@Autowired RestClient.Builder restClientBuilder, @LocalServerPort int port) {
		this.restClient = restClientBuilder.baseUrl("http://localhost:" + port).build();
	}

	@AfterEach
	void tearDown() {
		this.iamService.deleteIamRoleByOrgNameAndSpaceName(organizationName, spaceName);
	}

	@Test
	void provisioning(CapturedOutput capture) {
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "service_id": "%s",
					  "plan_id": "%s",
					  "context": {
					    "platform": "cloudfoundry",
					    "organization_guid": "%s",
					    "space_guid": "%s",
					    "organization_name": "%s",
					    "space_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(capture.toString()).containsPattern(
				"Created roleName=cf-demo-test-a2148c987d284bb6853c7761db9b9d5c roleArn=arn:aws:iam::\\d+:role/cf-demo-test-a2148c987d284bb6853c7761db9b9d5c");
	}

	@Test
	void provisioning_conflict(CapturedOutput capture) {
		this.iamService.createIamRole(UUID.randomUUID().toString(), organizationGuid, spaceGuid, organizationName,
				spaceName);
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "service_id": "%s",
					  "plan_id": "%s",
					  "context": {
					    "platform": "cloudfoundry",
					    "organization_guid": "%s",
					    "space_guid": "%s",
					    "organization_name": "%s",
					    "space_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("message")).isTrue();
		assertThat(body.get("message").asText()).isEqualTo(
				"The IAM role for the given org and space already exists. You can only create one IAM role per org and space.");
	}

	@Test
	void update() {
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
	void deprovisioning(CapturedOutput capture) {
		this.iamService.createIamRole(instanceId, organizationGuid, spaceGuid, organizationName, spaceName);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(capture.toString()).contains("Deleted roleName=cf-demo-test-a2148c987d284bb6853c7761db9b9d5c");
	}

}