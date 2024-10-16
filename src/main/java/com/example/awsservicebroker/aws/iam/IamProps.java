package com.example.awsservicebroker.aws.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "iam")
public record IamProps(String oidcProviderArn, @DefaultValue("cf") String roleNamePrefix,
		@DefaultValue("/cf-role/") String rolePath) {
}
