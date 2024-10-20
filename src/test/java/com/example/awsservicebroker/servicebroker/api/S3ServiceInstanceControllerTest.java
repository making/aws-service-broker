package com.example.awsservicebroker.servicebroker.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.awsservicebroker.aws.Instance;
import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.aws.s3.S3Service;
import com.example.awsservicebroker.config.TestConfig;
import com.example.awsservicebroker.servicebroker.AwsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static com.example.awsservicebroker.servicebroker.service.ServiceBrokerService.TAG_DELIMITER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = { "iam.oidc-provider-arn=arn:aws:iam::123456789012:oidc-provider/example.com",
				"logging.level.org.apache.http.wire=info" })
@ActiveProfiles("testcontainers")
@Import(TestConfig.class)
class S3ServiceInstanceControllerTest {

	RestClient restClient;

	@Autowired
	IamService iamService;

	@Autowired
	IamClient iamClient;

	@Autowired
	S3Service s3Service;

	@Autowired
	S3Client s3Client;

	@Autowired
	AwsRegionProvider regionProvider;

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
	void provisioning_with_role_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
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
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + TAG_DELIMITER + this.regionProvider.getRegion().id())
			.build());
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
	}

	@Test
	void provisioning_with_role_name_and_bucket_name() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = "test-" + UUID.randomUUID();
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
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
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).contains(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + TAG_DELIMITER + this.regionProvider.getRegion().id())
			.build());
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
	}

	@Test
	void provisioning_with_role_name_and_enable_versioning() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		boolean enableVersioning = true;
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
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
					    "role_name": "%s",
					    "enable_versioning": %s
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), enableVersioning, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).contains(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + TAG_DELIMITER + this.regionProvider.getRegion().id())
			.build());
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
	}

	@Test
	void provisioning_with_role_name_and_region() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
		BucketLocationConstraint region = BucketLocationConstraint.US_WEST_2;
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
					    "role_name": "%s",
					    "region": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), region, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).contains(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + TAG_DELIMITER + region)
			.build());
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
					instanceName, "not-exists", organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.has("message")).isTrue();
		assertThat(body.get("message").asText()).isEqualTo("The given role (role_name=not-exists) is not found");
	}

	@Test
	void provisioning_without_role_name() {
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
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
	void update_without_existing_instance() {
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
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
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
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}?service_id={serviceId}&plan_id={planId}", instanceId, serviceId,
					planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).doesNotContain(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + "|" + this.regionProvider.getRegion().id())
			.build());
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
	}

	@Test
	void deprovisioning_with_enable_versioning() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		boolean enableVersioning = true;
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
					    "enable_versioning": %s
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), enableVersioning, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		// put new versions
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		assertThat(this.s3Client.listObjectVersions(builder -> builder.bucket(bucketName)).versions()).hasSize(6);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}?service_id={serviceId}&plan_id={planId}", instanceId, serviceId,
					planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).doesNotContain(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + "|" + this.regionProvider.getRegion().id())
			.build());
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
	}

	@Test
	void update_with_enable_versioning_then_deprovisioning() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		boolean enableVersioning = true;
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
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		ResponseEntity<JsonNode> patched = this.restClient.patch()
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
					    "enable_versioning": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, enableVersioning, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
		// put new versions
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		// put new versions
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		assertThat(this.s3Client.listObjectVersions(builder -> builder.bucket(bucketName)).versions()).hasSize(9);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}?service_id={serviceId}&plan_id={planId}", instanceId, serviceId,
					planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).doesNotContain(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + "|" + this.regionProvider.getRegion().id())
			.build());
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
	}

	@Test
	void update_with_suspend_versioning_then_deprovisioning() {
		Role role = this.iamService.createIamRole(Instance.builder()
			.instanceId(UUID.randomUUID().toString())
			.instanceName(instanceName)
			.orgGuid(organizationGuid)
			.orgName(organizationName)
			.spaceGuid(spaceGuid)
			.spaceName(spaceName)
			.build());
		boolean enableVersioning = true;
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
					    "enable_versioning": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, role.roleName(), enableVersioning, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		String bucketName = this.s3Service.defaultBucketName(instanceId);
		assertThat(this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName))).isNotNull();
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		ResponseEntity<JsonNode> patched = this.restClient.patch()
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
					    "enable_versioning": "%s"
					  },
					  "organization_guid": "%s",
					  "space_guid": "%s",
					  "maintenance_info": {
					    "version": "2.1.1+abcdef"
					  }
					}
					""".formatted(serviceId, planId, organizationGuid, spaceGuid, organizationName, spaceName,
					instanceName, !enableVersioning, organizationGuid, spaceGuid))
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
		// put new versions
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		// put new versions
		for (int i = 0; i < 3; i++) {
			this.s3Service.putObject(bucketName, "test" + i, "This is test" + i);
		}
		assertThat(this.s3Client.listObjectVersions(builder -> builder.bucket(bucketName)).versions()).hasSize(6);
		ResponseEntity<JsonNode> response = this.restClient.delete()
			.uri("/v2/service_instances/{instanceId}?service_id={serviceId}&plan_id={planId}", instanceId, serviceId,
					planId)
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Tag> tags = this.iamClient.listRoleTags(builder -> builder.roleName(role.roleName())).tags();
		assertThat(tags).doesNotContain(Tag.builder()
			.key(AwsService.S3.roleTagKey(instanceId))
			.value(bucketName + "|" + this.regionProvider.getRegion().id())
			.build());
		assertThatThrownBy(() -> this.s3Client.getBucketLocation(builder -> builder.bucket(bucketName)))
			.isInstanceOf(NoSuchBucketException.class);
	}

}