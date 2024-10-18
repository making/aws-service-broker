package com.example.awsservicebroker.aws.dynamodb;

import com.example.awsservicebroker.utils.StringUtils;

import org.springframework.stereotype.Component;

@Component
public class DynamodbService {

	private final DynamodbProps props;

	public DynamodbService(DynamodbProps props) {
		this.props = props;
	}

	public String policyName(String instanceId) {
		return "dynamodb-" + StringUtils.removeHyphen(instanceId);
	}

	public String defaultTablePrefix(String instanceId) {
		return this.props.tablePrefix() + StringUtils.removeHyphen(instanceId);
	}

	public String buildTrustPolicyForTable(String tablePrefix) {
		return """
				{
				    "Version": "2012-10-17",
				    "Statement": [
				        {
				            "Effect": "Allow",
				            "Action": [
				                "dynamodb:*"
				            ],
				            "Resource": "arn:aws:dynamodb:*:*:table/%s*"
				        }
				    ]
				}
				""".formatted(tablePrefix);
	}

}
