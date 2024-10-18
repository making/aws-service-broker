package com.example.awsservicebroker.aws.dynamodb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dynamodb")
public record DynamodbProps(@DefaultValue("cf-") String tablePrefix) {
}
