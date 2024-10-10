package com.example.awsservicebroker.aws;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import software.amazon.awssdk.services.iam.model.Tag;

public record Instance(String instanceId, String instanceName, String orgGuid, String orgName, String spaceGuid,
		String spaceName) {

	public <T> List<T> toTags(BiFunction<String, String, T> tagCreator) {
		return List.of(tagCreator.apply("org_guid", orgGuid()), tagCreator.apply("org_name", orgName()),
				tagCreator.apply("space_name", spaceName()), tagCreator.apply("space_guid", spaceGuid()),
				tagCreator.apply("instance_id", instanceId()), tagCreator.apply("instance_name", instanceName()));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String instanceId;

		private String instanceName;

		private String orgGuid;

		private String orgName;

		private String spaceGuid;

		private String spaceName;

		public Builder instanceId(String instanceId) {
			this.instanceId = instanceId;
			return this;
		}

		public Builder instanceName(String instanceName) {
			this.instanceName = instanceName;
			return this;
		}

		public Builder orgGuid(String orgGuid) {
			this.orgGuid = orgGuid;
			return this;
		}

		public Builder orgName(String orgName) {
			this.orgName = orgName;
			return this;
		}

		public Builder spaceGuid(String spaceGuid) {
			this.spaceGuid = spaceGuid;
			return this;
		}

		public Builder spaceName(String spaceName) {
			this.spaceName = spaceName;
			return this;
		}

		public Instance build() {
			return new Instance(instanceId, instanceName, orgGuid, orgName, spaceGuid, spaceName);
		}

	}
}