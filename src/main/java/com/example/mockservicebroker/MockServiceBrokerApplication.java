package com.example.mockservicebroker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockServiceBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MockServiceBrokerApplication.class, args);
	}

}
