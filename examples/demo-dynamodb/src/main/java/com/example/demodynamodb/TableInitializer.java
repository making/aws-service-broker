package com.example.demodynamodb;

import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TableInitializer implements ApplicationRunner {

	private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

	private final DynamoDbClient dynamoDbClient;

	private final DynamoDbTableNameResolver dynamoDbTableNameResolver;

	private final Logger logger = LoggerFactory.getLogger(TableInitializer.class);

	public TableInitializer(DynamoDbEnhancedClient dynamoDbEnhancedClient, DynamoDbClient dynamoDbClient,
			DynamoDbTableNameResolver dynamoDbTableNameResolver) {
		this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
		this.dynamoDbClient = dynamoDbClient;
		this.dynamoDbTableNameResolver = dynamoDbTableNameResolver;
	}

	@Override
	public void run(ApplicationArguments args) {
		String tableName = this.dynamoDbTableNameResolver.resolve(Movie.class);

		// Check if the table already exists
		if (doesTableExist(tableName)) {
			logger.info("Table {} already exists. Skipping creation.", tableName);
		}
		else {
			// Define the Movie table
			DynamoDbTable<Movie> movieTable = this.dynamoDbEnhancedClient.table(tableName,
					TableSchema.fromBean(Movie.class));
			// Create the table
			movieTable.createTable(builder -> builder
				.provisionedThroughput(throughput -> throughput.readCapacityUnits(5L).writeCapacityUnits(5L))
				.globalSecondaryIndices(
						// title-index
						EnhancedGlobalSecondaryIndex.builder()
							.indexName("title-index")
							.projection(projection -> projection.projectionType(ProjectionType.ALL))
							.provisionedThroughput(
									throughput -> throughput.readCapacityUnits(5L).writeCapacityUnits(5L))
							.build(),
						// genre-index
						EnhancedGlobalSecondaryIndex.builder()
							.indexName("genre-index")
							.projection(projection -> projection.projectionType(ProjectionType.ALL))
							.provisionedThroughput(
									throughput -> throughput.readCapacityUnits(5L).writeCapacityUnits(5L))
							.build()));
			logger.info("Table {} created successfully", tableName);
		}
	}

	// Method to check if a table already exists
	boolean doesTableExist(String tableName) {
		try {
			DescribeTableResponse response = this.dynamoDbClient.describeTable(builder -> builder.tableName(tableName));
			return response.table() != null;
		}
		catch (ResourceNotFoundException e) {
			// Table does not exist
			return false;
		}
	}

}
