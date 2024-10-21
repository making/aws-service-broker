package com.example.demos3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoS3Application {

	public static void main(String[] args) {
		SpringApplication.run(DemoS3Application.class, args);
	}

}
