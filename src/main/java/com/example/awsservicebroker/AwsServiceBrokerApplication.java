package com.example.awsservicebroker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AwsServiceBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AwsServiceBrokerApplication.class, args);
	}

}
