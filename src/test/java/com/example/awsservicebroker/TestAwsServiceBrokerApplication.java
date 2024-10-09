package com.example.awsservicebroker;

import com.example.awsservicebroker.config.TestcontainersConfig;

import org.springframework.boot.SpringApplication;

public class TestAwsServiceBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.from(AwsServiceBrokerApplication::main).with(TestcontainersConfig.class).run(args);
	}

}
