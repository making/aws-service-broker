package com.example.demos3;

import org.springframework.boot.SpringApplication;

public class TestDemoS3Application {

	public static void main(String[] args) {
		SpringApplication.from(DemoS3Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
