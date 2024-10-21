package com.example.awsservicebroker.servicebroker.api;

import java.util.List;
import java.util.UUID;

import com.example.awsservicebroker.aws.Instance;
import com.example.awsservicebroker.aws.dynamodb.DynamodbService;
import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.config.TestConfig;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
		properties = { "iam.oidc-provider-arn=arn:aws:iam::123456789012:oidc-provider/example.com",
				"logging.level.org.apache.http.wire=info", "spring.cloud.aws.s3.region=ap-northeast-1" })
@ActiveProfiles("testcontainers")
@Import(TestConfig.class)
public class DynamodbServiceInstanceControllerTest {

	RestClient restClient;

	@Autowired
	IamService iamService;

	@Autowired
	IamClient iamClient;

	@Autowired
	DynamodbService dynamodbService;

	@Autowired
	DynamoDbClient dynamoDbClient;

	@Autowired
	DynamoDbEnhancedClient dynamoDbEnhancedClient;

	String bindingId = "8f0b2a93-ca8b-4850-a12a-39a82a17148b";

	String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";

	String instanceName = "foo";

	String serviceId = AwsService.DYNAMODB.serviceId();

	String planId = "a42e2c0c-64e0-41c5-a59c-be52c592812e";

	String organizationGuid = "4b84793c-f3ea-4a55-92b7-942726aac163";

	String spaceGuid = "34e1bb23-0e76-4aad-95d7-1abe3ea1dcd8";

	String appGuid = "ee4a897f-a9ce-42c9-8318-4775d692836b";

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
	void provisioning_with_role_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
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
					    "space_name": "%s",
					    "instance_name": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).contains(Tag.builder()
			.key(AwsService.DYNAMODB.roleTagKey(instanceId))
			.value("cf-" + StringUtils.removeHyphen(instanceId) + "-")
			.build());
	}

	@Test
	void provisioning_without_role_name() {
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
					    "space_name": "%s",
					    "instance_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void provisioning_with_missing_role_name() {
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
					    "space_name": "%s",
					    "instance_name": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, "missing_role", organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void deprovisioning() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		this.restClient.put()
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
					    "space_name": "%s",
					    "instance_name": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		String tablePrefix = "cf-" + StringUtils.removeHyphen(instanceId) + "-";
		this.dynamoDbEnhancedClient.table(tablePrefix + "movie1", TableSchema.fromBean(Movie.class)).createTable();
		this.dynamoDbEnhancedClient.table(tablePrefix + "movie2", TableSchema.fromBean(Movie.class)).createTable();
		List<String> tableNames = this.dynamoDbClient.listTables().tableNames();
		assertThat(tableNames).contains(tablePrefix + "movie1");
		assertThat(tableNames).contains(tablePrefix + "movie2");
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}?service_id={serviceId}&plan_id={planId}", instanceId, serviceId,
					planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags)
			.doesNotContain(Tag.builder().key(AwsService.DYNAMODB.roleTagKey(instanceId)).value(tablePrefix).build());
		tableNames = this.dynamoDbClient.listTables().tableNames();
		assertThat(tableNames).doesNotContain(tablePrefix + "movie1");
		assertThat(tableNames).doesNotContain(tablePrefix + "movie2");
	}

	@DynamoDbBean
	public static class Movie {

		private UUID movieId;

		private String title;

		public void setMovieId(UUID movieId) {
			this.movieId = movieId;
		}

		@DynamoDbPartitionKey
		public UUID getMovieId() {
			return movieId;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getTitle() {
			return title;
		}

	}

}
