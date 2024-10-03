package com.example.mockservicebroker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "service-broker")
public record ServiceBrokerProps(@DefaultValue("classpath:catalog.yaml") Resource catalog) {
}
