package com.example.awsservicebroker.aws.iam;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.example.awsservicebroker.aws.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.Tag;

import org.springframework.stereotype.Component;

@Component
public class IamService {

	private final IamClient iamClient;

	private final IamProps iamProps;

	private final Logger logger = LoggerFactory.getLogger(IamService.class);

	public static final String ROLE_NAME_DELIMITER = "_";

	public IamService(IamClient iamClient, IamProps iamProps) {
		this.iamClient = iamClient;
		this.iamProps = iamProps;
	}

	String roleName(String instanceName, String orgName, String spaceName) {
		return String.join(ROLE_NAME_DELIMITER, this.iamProps.roleNamePrefix(), orgName, spaceName, instanceName);
	}

	public Role createIamRole(Instance instance) {
		String oidcProviderArn = this.iamProps.oidcProviderArn();
		String oidcProviderDomain = oidcProviderArn.split("/")[1];
		String roleName = this.roleName(instance.instanceName(), instance.orgName(), instance.spaceName());
		String assumeRolePolicyDocument = buildAssumeRolePolicyDocument(oidcProviderArn, oidcProviderDomain,
				instance.orgGuid(), instance.spaceGuid());
		logger.info("Creating role={} policy={}", roleName, assumeRolePolicyDocument);
		CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
			.roleName(roleName)
			.path(this.iamProps.rolePath())
			.assumeRolePolicyDocument(assumeRolePolicyDocument)
			.tags(instance.toTags((key, value) -> Tag.builder().key(key).value(value).build()))
			.build();
		CreateRoleResponse createRoleResponse = this.iamClient.createRole(createRoleRequest);
		Role role = createRoleResponse.role();
		logger.info("Created roleName={} roleArn={}", role.roleName(), role.arn());
		return role;
	}

	public void attachInlinePolicyToRole(String roleName, String policyName, String policyDocument) {
		logger.info("Attaching inline to role={} policy={} policy_document={}", roleName, policyName, policyDocument);
		this.iamClient
			.putRolePolicy(builder -> builder.roleName(roleName).policyName(policyName).policyDocument(policyDocument));
		logger.info("Attached inline to role={} policy={}", roleName, policyName);
	}

	public void detachInlinePolicyFromRole(String roleName, String policyName) {
		logger.info("Detaching inline policy={} from role={}", policyName, roleName);
		this.iamClient.deleteRolePolicy(builder -> builder.roleName(roleName).policyName(policyName));
		logger.info("Detached inline policy={} from role={}", policyName, roleName);
	}

	public Optional<Role> findRoleByPolicyName(String policyName) {
		return this.iamClient.listRoles(builder -> builder.pathPrefix(this.iamProps.rolePath()).maxItems(1000))
			.roles()
			.stream()
			.filter(role -> this.iamClient.listRolePolicies(builder -> builder.roleName(role.roleName()).maxItems(1000))
				.policyNames()
				.stream()
				.anyMatch(policy -> Objects.equals(policy, policyName)))
			.findAny();
	}

	public Optional<Role> findRoleByRoleName(String roleName) {
		return this.iamClient.listRoles(builder -> builder.pathPrefix(this.iamProps.rolePath()).maxItems(1000))
			.roles()
			.stream()
			.filter(role -> Objects.equals(roleName, role.roleName()))
			.findAny();
	}

	public Optional<Role> findRoleByTags(Predicate<Map<String, String>> predicate) {
		return this.iamClient.listRoles(builder -> builder.pathPrefix(this.iamProps.rolePath()).maxItems(1000))
			.roles()
			.stream()
			.filter(role -> {
				Map<String, String> tagMap = this.iamClient
					.listRoleTags(builder -> builder.roleName(role.roleName()).maxItems(1000))
					.tags()
					.stream()
					.collect(Collectors.toMap(Tag::key, Tag::value));
				return predicate.test(tagMap);
			})
			.findAny();
	}

	public Optional<Role> findRoleByInstanceId(String instanceId) {
		return this.findRoleByTags(
				tagMap -> tagMap.containsKey("instance_id") && tagMap.get("instance_id").equals(instanceId));
	}

	public Optional<Role> findRoleByOrgNameAndSpaceName(String orgName, String spaceName) {
		return this.findRoleByTags(tagMap -> Objects.equals(tagMap.get("org_name"), orgName)
				&& Objects.equals(tagMap.get("space_name"), spaceName));
	}

	public void deleteIamRoleByInstanceId(String instanceId) {
		this.findRoleByInstanceId(instanceId).ifPresent(this::deleteRole);
	}

	public void deleteIamRoleByOrgNameAndSpaceName(String orgName, String spaceName) {
		this.findRoleByOrgNameAndSpaceName(orgName, spaceName).ifPresent(this::deleteRole);
	}

	void deleteRole(Role role) {
		String roleName = role.roleName();
		this.detachPoliciesFromRole(roleName);
		this.deleteInlinePoliciesFromRole(roleName);
		DeleteRoleRequest deleteRoleRequest = DeleteRoleRequest.builder().roleName(roleName).build();
		logger.info("Deleting roleName={}", roleName);
		this.iamClient.deleteRole(deleteRoleRequest);
		logger.info("Deleted roleName={}", roleName);
	}

	void detachPoliciesFromRole(String roleName) {
		ListAttachedRolePoliciesResponse listAttachedPoliciesResponse = this.iamClient.listAttachedRolePolicies(
				builder -> builder.pathPrefix(this.iamProps.rolePath()).roleName(roleName).build());
		List<AttachedPolicy> attachedPolicies = listAttachedPoliciesResponse.attachedPolicies();
		for (AttachedPolicy policy : attachedPolicies) {
			logger.info("Detaching policy={} roleName={}", policy.policyName(), roleName);
			this.iamClient
				.detachRolePolicy(builder -> builder.roleName(roleName).policyArn(policy.policyArn()).build());
			logger.info("Detached policy={} roleName={}", policy.policyName(), roleName);
		}
	}

	void deleteInlinePoliciesFromRole(String roleName) {
		ListRolePoliciesResponse listRolePoliciesResponse = this.iamClient
			.listRolePolicies(builder -> builder.roleName(roleName).build());
		List<String> inlinePolicies = listRolePoliciesResponse.policyNames();

		for (String policyName : inlinePolicies) {
			logger.info("Deleting inline policy={} roleName={}", policyName, roleName);
			this.iamClient.deleteRolePolicy(builder -> builder.roleName(roleName).policyName(policyName).build());
			logger.info("Deleted inline policy={} roleName={}", policyName, roleName);
		}
	}

	public List<Tag> listRoleTags(String roleName) {
		return this.iamClient.listRoleTags(builder -> builder.roleName(roleName).maxItems(1000)).tags();
	}

	public void addRoleTags(String roleName, List<Tag> tags) {
		logger.info("Adding tags to role roleName={} tags={}", roleName, tags);
		this.iamClient.tagRole(builder -> builder.roleName(roleName).tags(tags).build());
		logger.info("Added tags to role roleName={} tags={}", roleName, tags);
	}

	public void removeRoleTags(String roleName, List<String> tagKeys) {
		logger.info("Removing tags to role roleName={} tagKeys={}", roleName, tagKeys);
		this.iamClient.untagRole(builder -> builder.roleName(roleName).tagKeys(tagKeys));
		logger.info("Removed tags to role roleName={} tagKeys={}", roleName, tagKeys);
	}

	private static String buildAssumeRolePolicyDocument(String oidcProviderArn, String oidcProviderDomain,
			String orgGuid, String spaceGuid) {
		return """
				{
				  "Version": "2012-10-17",
				  "Statement": [
				    {
				      "Effect": "Allow",
				      "Principal": {
				        "Federated": "%s"
				      },
				      "Action": "sts:AssumeRoleWithWebIdentity",
				      "Condition": {
				        "StringLike": {
				          "%s:sub": "%s:%s:*",
				          "%s:aud": "sts.amazonaws.com"
				        }
				      }
				    }
				  ]
				}""".formatted(oidcProviderArn, oidcProviderDomain, orgGuid, spaceGuid, oidcProviderDomain);
	}

}
