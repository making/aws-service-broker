package com.example.awsservicebroker.servicebroker.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.example.awsservicebroker.aws.Instance;
import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.aws.s3.S3Service;
import com.example.awsservicebroker.config.TestConfig;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.example.awsservicebroker.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.s3.model.Tag;

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
class S3ServiceBindingControllerTest {

	RestClient restClient;

	@Autowired
	IamService iamService;

	@Autowired
	IamClient iamClient;

	@Autowired
	S3Service s3Service;

	String bindingId = "8f0b2a93-ca8b-4850-a12a-39a82a17148b";

	String instanceId = "a2148c98-7d28-4bb6-853c-7761db9b9d5c";

	String instanceName = "foo";

	String serviceId = AwsService.S3.serviceId();

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
		this.s3Service.deleteBucketByInstanceId(instanceId);
	}

	@Test
	void cleanUp() {

	}

	@Test
	void bind_role_already_associated() {
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
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					  }
					}
					""".formatted(serviceId, planId, appGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("credentials")).isTrue();
		assertThat(body.get("credentials").get("region")).isEqualTo(new TextNode("ap-northeast-1"));
		String bucketName = "cf-" + StringUtils.removeHyphen(instanceId);
		assertThat(body.get("credentials").get("bucket_name")).isEqualTo(new TextNode(bucketName));
		assertThat(body.get("credentials").get("role_name")).isEqualTo(new TextNode(role.roleName()));
		assertThat(body.get("credentials").get("role_arn")).isEqualTo(new TextNode(role.arn()));
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		assertThat(tags).contains(Tag.builder().key("role_name").value(role.roleName()).build());
		String policyName = "s3-" + bucketName;
		assertThat(tags).contains(Tag.builder().key("policy_name").value(policyName).build());
		List<String> policyNames = this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()))
			.policyNames();
		assertThat(policyNames).contains(policyName);
	}

	@Test
	void bind_role_already_associated_with_custom_bucket_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = "test-" + UUID.randomUUID();
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
					    "role_name": "%s",
					    "bucket_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), bucketName, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					  }
					}
					""".formatted(serviceId, planId, appGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("credentials")).isTrue();
		assertThat(body.get("credentials").get("region")).isEqualTo(new TextNode("ap-northeast-1"));
		assertThat(body.get("credentials").get("bucket_name")).isEqualTo(new TextNode(bucketName));
		assertThat(body.get("credentials").get("role_name")).isEqualTo(new TextNode(role.roleName()));
		assertThat(body.get("credentials").get("role_arn")).isEqualTo(new TextNode(role.arn()));
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		assertThat(tags).contains(Tag.builder().key("role_name").value(role.roleName()).build());
		String policyName = "s3-" + bucketName;
		assertThat(tags).contains(Tag.builder().key("policy_name").value(policyName).build());
		List<String> policyNames = this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()))
			.policyNames();
		assertThat(policyNames).contains(policyName);
	}

	@Test
	void bind_role_not_yet_associated() {
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
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  }
					}
					""".formatted(serviceId, planId, appGuid, role.roleName()))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("credentials")).isTrue();
		assertThat(body.get("credentials").get("region")).isEqualTo(new TextNode("ap-northeast-1"));
		String bucketName = "cf-" + StringUtils.removeHyphen(instanceId);
		assertThat(body.get("credentials").get("bucket_name")).isEqualTo(new TextNode(bucketName));
		assertThat(body.get("credentials").get("role_name")).isEqualTo(new TextNode(role.roleName()));
		assertThat(body.get("credentials").get("role_arn")).isEqualTo(new TextNode(role.arn()));
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		String binding = StringUtils.removeHyphen(bindingId);
		assertThat(tags).contains(Tag.builder().key("role_name_" + binding).value(role.roleName()).build());
		String policyName = "s3-" + bucketName + "-" + binding;
		assertThat(tags).contains(Tag.builder().key("policy_name_" + binding).value(policyName).build());
		List<String> policyNames = this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()))
			.policyNames();
		assertThat(policyNames).contains(policyName);
	}

	@Test
	void bind_role_is_missing() {
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
		ResponseEntity<JsonNode> response = this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  }
					}
					""".formatted(serviceId, planId, appGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("message")).isTrue();
		assertThat(body.get("message").asText()).isEqualTo(
				"If you do not specify the 'role_name' parameter in the service instance, you must specify the 'role_name' parameter in the service binding.");
	}

	@Test
	void unbind_role_associated_provisioning_phase() {
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
		String bucketName = Objects
			.requireNonNull(this.restClient.put()
				.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "context": {
						    "platform": "cloudfoundry"
						  },
						  "service_id": "%s",
						  "plan_id": "%s",
						  "bind_resource": {
						    "app_guid": "%s"
						  },
						  "parameters": {
						  }
						}
						""".formatted(serviceId, planId, appGuid))
				.retrieve()
				.toEntity(JsonNode.class)
				.getBody())
			.get("credentials")
			.get("bucket_name")
			.asText();
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}?service_id={serviceId}&plan_id={planId}",
					instanceId, bindingId, serviceId, planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		assertThat(tags).contains(Tag.builder().key("role_name").value(role.roleName()).build());
		String policyName = "s3-" + bucketName;
		assertThat(tags).contains(Tag.builder().key("policy_name").value(policyName).build());
	}

	@Test
	void unbind_role_associated_provisioning_phase_with_custom_bucket_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = "test-" + UUID.randomUUID();
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
					    "role_name": "%s",
					    "bucket_name": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), bucketName, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					  }
					}
					""".formatted(serviceId, planId, appGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}?service_id={serviceId}&plan_id={planId}",
					instanceId, bindingId, serviceId, planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		assertThat(tags).contains(Tag.builder().key("role_name").value(role.roleName()).build());
		String policyName = "s3-" + bucketName;
		assertThat(tags).contains(Tag.builder().key("policy_name").value(policyName).build());
	}

	@Test
	void unbind_role_associated_binding_phase() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = "test-" + UUID.randomUUID();
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
						    "bucket_name": "%s"
						  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, bucketName, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  }
					}
					""".formatted(serviceId, planId, appGuid, role.roleName()))
			.retrieve()
			.toEntity(JsonNode.class);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}?service_id={serviceId}&plan_id={planId}",
					instanceId, bindingId, serviceId, planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		String binding = StringUtils.removeHyphen(bindingId);
		assertThat(tags).doesNotContain(Tag.builder().key("role_name_" + binding).value(role.roleName()).build());
		String policyName = "s3-" + bucketName + "-" + binding;
		assertThat(tags).doesNotContain(Tag.builder().key("policy_name_" + binding).value(policyName).build());
		List<String> policyNames = this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()))
			.policyNames();
		assertThat(policyNames).doesNotContain(policyName);
	}

	@Test
	void unbind_role_associated_binding_phase_with_custom_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = "test-" + UUID.randomUUID();
		this.restClient.put()
			.uri("/v2/service_instances/{instanceId}", instanceId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "service_id": "%s",
					  "plan_id": "%s",
					  "parameters": {
					    "bucket_name": "%s"
					  },
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
					""".formatted(serviceId, planId, bucketName, organizationGuid, spaceGuid, organizationName,
					spaceName, instanceName, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		this.restClient.put()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}", instanceId, bindingId)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{
					  "context": {
					    "platform": "cloudfoundry"
					  },
					  "service_id": "%s",
					  "plan_id": "%s",
					  "bind_resource": {
					    "app_guid": "%s"
					  },
					  "parameters": {
					    "role_name": "%s"
					  }
					}
					""".formatted(serviceId, planId, appGuid, role.roleName()))
			.retrieve()
			.toEntity(JsonNode.class);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}/service_bindings/{bindingId}?service_id={serviceId}&plan_id={planId}",
					instanceId, bindingId, serviceId, planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.s3Service.listBucketTags(bucketName);
		String binding = StringUtils.removeHyphen(bindingId);
		assertThat(tags).doesNotContain(Tag.builder().key("role_name_" + binding).value(role.roleName()).build());
		String policyName = "s3-" + bucketName + "-" + binding;
		assertThat(tags).doesNotContain(Tag.builder().key("policy_name_" + binding).value(policyName).build());
		List<String> policyNames = this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()))
			.policyNames();
		assertThat(policyNames).doesNotContain(policyName);
	}

}