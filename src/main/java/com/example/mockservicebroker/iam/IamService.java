package com.example.mockservicebroker.iam;

import java.util.List;
import java.util.Optional;

import com.example.mockservicebroker.IamProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.DeleteRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListRolePoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.Role;

import org.springframework.stereotype.Component;

@Component
public class IamService {

	private final IamClient iamClient;

	private final IamProps iamProps;

	private final Logger logger = LoggerFactory.getLogger(IamService.class);

	public IamService(IamClient iamClient, IamProps iamProps) {
		this.iamClient = iamClient;
		this.iamProps = iamProps;
	}

	public Role createIamRole(String instanceId, String orgGuid, String spaceGuid, String orgName, String spaceName) {
		String oidcProviderArn = this.iamProps.oidcProviderArn();
		String oidcProviderDomain = oidcProviderArn.split("/")[1];
		String roleName = this.iamProps.roleNamePrefix() + orgName + "-" + spaceName + "-" + removeHyphen(instanceId);
		String assumeRolePolicyDocument = buildAssumeRolePolicyDocument(oidcProviderArn, oidcProviderDomain, orgGuid,
				spaceGuid);
		logger.info("Creating role={} policy={}", roleName, assumeRolePolicyDocument);
		CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
				.roleName(roleName)
				.assumeRolePolicyDocument(assumeRolePolicyDocument)
				.build();
		CreateRoleResponse createRoleResponse = this.iamClient.createRole(createRoleRequest);
		Role role = createRoleResponse.role();
		logger.info("Created roleName={} roleArn={}", role.roleName(), role.arn());
		return role;
	}

	public Optional<Role> findRoleByInstanceId(String instanceId) {
		return this.iamClient.listRoles().roles().stream().filter(role -> {
			String roleName = role.roleName();
			return roleName.startsWith(this.iamProps.roleNamePrefix())
				   && roleName.endsWith("-" + removeHyphen(instanceId));
		}).findAny();
	}

	public Optional<Role> findRoleByOrgNameAndSpaceName(String orgName, String spaceName) {
		return this.iamClient.listRoles().roles().stream().filter(role -> {
			String roleName = role.roleName();
			return roleName.startsWith(this.iamProps.roleNamePrefix() + orgName + "-" + spaceName);
		}).findAny();
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
		ListAttachedRolePoliciesRequest listAttachedPoliciesRequest = ListAttachedRolePoliciesRequest.builder()
				.roleName(roleName)
				.build();
		ListAttachedRolePoliciesResponse listAttachedPoliciesResponse = this.iamClient.listAttachedRolePolicies(listAttachedPoliciesRequest);
		List<AttachedPolicy> attachedPolicies = listAttachedPoliciesResponse.attachedPolicies();
		for (AttachedPolicy policy : attachedPolicies) {
			DetachRolePolicyRequest detachRolePolicyRequest = DetachRolePolicyRequest.builder()
					.roleName(roleName)
					.policyArn(policy.policyArn())
					.build();
			logger.info("Detaching policy={} roleName={}", policy.policyName(), roleName);
			this.iamClient.detachRolePolicy(detachRolePolicyRequest);
			logger.info("Detached policy={} roleName={}", policy.policyName(), roleName);
		}
	}

	void deleteInlinePoliciesFromRole(String roleName) {
		ListRolePoliciesRequest listRolePoliciesRequest = ListRolePoliciesRequest.builder()
				.roleName(roleName)
				.build();

		ListRolePoliciesResponse listRolePoliciesResponse = this.iamClient.listRolePolicies(listRolePoliciesRequest);
		List<String> inlinePolicies = listRolePoliciesResponse.policyNames();

		for (String policyName : inlinePolicies) {
			DeleteRolePolicyRequest deleteRolePolicyRequest = DeleteRolePolicyRequest.builder()
					.roleName(roleName)
					.policyName(policyName)
					.build();
			logger.info("Deleting inline policy={} roleName={}", policyName, roleName);
			this.iamClient.deleteRolePolicy(deleteRolePolicyRequest);
			logger.info("Deleted inline policy={} roleName={}", policyName, roleName);
		}
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

	static String removeHyphen(String s) {
		return s.replaceAll("-", "");
	}

}
