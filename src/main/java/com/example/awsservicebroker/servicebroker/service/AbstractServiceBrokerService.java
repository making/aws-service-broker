package com.example.awsservicebroker.servicebroker.service;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.example.awsservicebroker.aws.iam.IamService;
import com.example.awsservicebroker.servicebroker.AwsService;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public abstract class AbstractServiceBrokerService implements ServiceBrokerService {

	protected final IamService iamService;

	protected AbstractServiceBrokerService(IamService iamService) {
		this.iamService = iamService;
	}

	protected abstract AwsService awsService();

	protected record PolicyAndResult<T>(String policy, T result) {
	}

	protected record RoleAndResult<T>(Role role, T result) {
	}

	protected record RoleAndRoleTagValue(Role role, String roleTagValue) {
	}

	final protected void addRoleTag(String roleName, String instanceId, Supplier<String> roleTagValueSupplier) {
		this.iamService.findRoleByRoleName(roleName)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The given role (role_name=%s) is not found".formatted(roleName)));
		String roleTagKey = awsService().roleTagKey(instanceId);
		String roleTagValue = roleTagValueSupplier.get();
		this.iamService.addRoleTags(roleName, List.of(Tag.builder().key(roleTagKey).value(roleTagValue).build()));
	}

	final protected <T> RoleAndResult<T> attachInlinePolicy(String instanceId, String bindingId,
			Function<String, PolicyAndResult<T>> roleTagValueMapper) {
		RoleAndRoleTagValue roleAndRoleTagValue = this.findRoleAndRoleTagValue(instanceId);
		Role role = roleAndRoleTagValue.role();
		PolicyAndResult<T> policyAndResult = roleTagValueMapper.apply(roleAndRoleTagValue.roleTagValue());
		String policyName = awsService().policyName(instanceId, bindingId);
		this.iamService.attachInlinePolicyToRole(role.roleName(), policyName, policyAndResult.policy());
		return new RoleAndResult<>(role, policyAndResult.result());
	}

	final protected RoleAndRoleTagValue findRoleAndRoleTagValue(String instanceId) {
		String roleTagKey = awsService().roleTagKey(instanceId);
		Role role = this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "The instance has gone."));
		List<Tag> tags = this.iamService.listRoleTags(role.roleName());
		Tag roleTag = tags.stream()
			.filter(tag -> tag.key().equals(roleTagKey))
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
					"The corresponding tag '%s' is not found in the role '%s'.".formatted(roleTagKey,
							role.roleName())));
		return new RoleAndRoleTagValue(role, roleTag.value());
	}

	final protected void detachInlinePolicy(String instanceId, String bindingId, Runnable beforeDetach) {
		String roleTagKey = awsService().roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey)).ifPresent(role -> {
			beforeDetach.run();
			String policyName = awsService().policyName(instanceId, bindingId);
			this.iamService.detachInlinePolicyFromRole(role.roleName(), policyName);
		});
	}

	final protected void removeRoleTag(String instanceId, Consumer<String> roleTagValueConsumer) {
		String roleTagKey = awsService().roleTagKey(instanceId);
		this.iamService.findRoleByTags(tagMap -> tagMap.containsKey(roleTagKey)).ifPresent(role -> {
			List<Tag> tags = this.iamService.listRoleTags(role.roleName());
			tags.stream().filter(tag -> tag.key().equals(roleTagKey)).findAny().ifPresent(tag -> {
				roleTagValueConsumer.accept(tag.value());
			});
			this.iamService.removeRoleTags(role.roleName(), List.of(roleTagKey));
		});
	}

}
