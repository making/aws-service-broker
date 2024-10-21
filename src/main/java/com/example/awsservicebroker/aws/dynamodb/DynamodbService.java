package com.example.awsservicebroker.aws.dynamodb;

import com.example.awsservicebroker.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import org.springframework.stereotype.Component;

@Component
public class DynamodbService {

	private final DynamoDbClient dynamoDbClient;

	private final DynamodbProps props;

	private final Logger logger = LoggerFactory.getLogger(DynamodbService.class);

	public DynamodbService(DynamoDbClient dynamoDbClient, DynamodbProps props) {
		this.dynamoDbClient = dynamoDbClient;
		this.props = props;
	}

	public String defaultTablePrefix(String instanceId) {
		return this.props.tablePrefix() + StringUtils.removeHyphen(instanceId) + "-";
	}

	public void deleteTableWithPrefix(String tablePrefix) {
		String lastEvaluatedTableName = null;
		do {
			ListTablesRequest listTablesRequest = ListTablesRequest.builder()
				.exclusiveStartTableName(lastEvaluatedTableName) // For pagination
				.build();
			ListTablesResponse listTablesResponse = this.dynamoDbClient.listTables(listTablesRequest);
			for (String tableName : listTablesResponse.tableNames()) {
				if (tableName.startsWith(tablePrefix)) {
					logger.info("Deleting table {}", tableName);
					dynamoDbClient.deleteTable(builder -> builder.tableName(tableName));
					logger.info("Deleted table {}", tableName);
				}
			}
			lastEvaluatedTableName = listTablesResponse.lastEvaluatedTableName();
		}
		while (lastEvaluatedTableName != null);
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
