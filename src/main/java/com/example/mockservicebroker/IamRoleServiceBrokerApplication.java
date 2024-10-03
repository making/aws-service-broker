package com.example.mockservicebroker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IamRoleServiceBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IamRoleServiceBrokerApplication.class, args);
	}

}
