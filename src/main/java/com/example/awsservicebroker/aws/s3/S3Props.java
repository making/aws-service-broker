package com.example.awsservicebroker.aws.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "s3")
public record S3Props(@DefaultValue("cf-") String bucketNamePrefix) {
}
