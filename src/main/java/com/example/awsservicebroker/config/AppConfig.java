package com.example.awsservicebroker.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.securityMatcher(EndpointRequest.toAnyEndpoint())
			.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.build();
	}

}
