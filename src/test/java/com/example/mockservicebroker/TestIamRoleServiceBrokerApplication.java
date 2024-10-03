package com.example.mockservicebroker;

import com.example.mockservicebroker.config.TestcontainersConfig;

import org.springframework.boot.SpringApplication;

public class TestIamRoleServiceBrokerApplication {

	public static void main(String[] args) {
		SpringApplication.from(IamRoleServiceBrokerApplication::main).with(TestcontainersConfig.class).run(args);
	}

}
