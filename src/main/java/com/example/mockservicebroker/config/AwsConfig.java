package com.example.mockservicebroker.config;

import io.awspring.cloud.autoconfigure.core.AwsClientBuilderConfigurer;
import io.awspring.cloud.autoconfigure.core.AwsConnectionDetails;
import software.amazon.awssdk.services.iam.IamClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AwsConfig {

	@Bean
	public IamClient iamClient(AwsClientBuilderConfigurer awsClientBuilderConfigurer,
			ObjectProvider<AwsConnectionDetails> connectionDetails) {
		return awsClientBuilderConfigurer.configure(IamClient.builder(), null, connectionDetails.getIfAvailable(), null)
			.build();
	}

}
