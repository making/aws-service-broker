package com.example.mockservicebroker.config;

import java.io.IOException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

	@Profile("testcontainers")
	@TestConfiguration(proxyBeanMethods = false)
	@Import(TestcontainersConfig.class)
	public static class TestContainers {

	}

	@Bean
	public RestClientCustomizer restClientCustomizer() {
		return builder -> builder.defaultHeaders(httpHeaders -> httpHeaders.setBasicAuth("admin", "password"))
			.requestFactory(ClientHttpRequestFactories.get(JdkClientHttpRequestFactory.class,
					ClientHttpRequestFactorySettings.DEFAULTS))
			.defaultStatusHandler(__ -> true, (request, response) -> {

			});
	}

}
