package com.example.demodynamodb;

import org.springframework.boot.SpringApplication;

public class TestDemoDynamodbApplication {

	public static void main(String[] args) {
		SpringApplication.from(DemoDynamodbApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
