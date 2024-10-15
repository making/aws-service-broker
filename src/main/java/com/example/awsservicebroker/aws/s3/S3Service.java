package com.example.awsservicebroker.aws.s3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.awsservicebroker.aws.Instance;
import com.example.awsservicebroker.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class S3Service {

	private final S3Client s3Client;

	private final S3Props s3Props;

	private final Region region;

	private final Logger logger = LoggerFactory.getLogger(S3Service.class);

	public S3Service(S3Client s3Client, S3Props s3Props, AwsRegionProvider awsRegionProvider) {
		this.s3Client = s3Client;
		this.s3Props = s3Props;
		this.region = awsRegionProvider.getRegion();
	}

	public String defaultBucketName(String instanceId) {
		return this.s3Props.bucketNamePrefix() + StringUtils.removeHyphen(instanceId);
	}

	public String createBucket(Instance instance, @Nullable String bucketName) {
		String bucketNameToCreate = bucketName == null ? this.defaultBucketName(instance.instanceId()) : bucketName;
		logger.info("Creating bucket bucketName={} region={}", bucketNameToCreate, this.region.id());
		CreateBucketResponse response = this.s3Client.createBucket(builder -> builder
			.createBucketConfiguration(CreateBucketConfiguration.builder().locationConstraint(this.region.id()).build())
			.bucket(bucketNameToCreate));
		logger.info("Created bucket bucketName={} location={}", bucketNameToCreate, response.location());
		this.putBucketTags(bucketNameToCreate,
				instance.toTags((key, value) -> Tag.builder().key(key).value(value).build()), false);
		return bucketNameToCreate;
	}

	public List<Tag> listBucketTags(String bucketName) {
		return this.s3Client.getBucketTagging(builder -> builder.bucket(bucketName)).tagSet();
	}

	public void putBucketTags(String bucketName, List<Tag> tags, boolean append) {
		List<Tag> tagsToPut = new ArrayList<>(tags);
		if (append) {
			tagsToPut.addAll(this.listBucketTags(bucketName));
		}
		logger.info("Putting tags to bucket bucketName={} tags={}", bucketName, tagsToPut);
		this.s3Client.putBucketTagging(
				builder -> builder.bucket(bucketName).tagging(Tagging.builder().tagSet(tagsToPut).build()));
	}

	public void removeBucketTags(String bucketName, List<Tag> tags) {
		List<Tag> tagsToPut = new ArrayList<>(this.listBucketTags(bucketName));
		tagsToPut.removeAll(tags);
		logger.info("Putting tags to bucket bucketName={} tags={}", bucketName, tagsToPut);
		this.s3Client.putBucketTagging(
				builder -> builder.bucket(bucketName).tagging(Tagging.builder().tagSet(tagsToPut).build()));
	}

	public void putObject(String bucketName, String objectKey, String content) {
		this.s3Client.putObject(builder -> builder.bucket(bucketName).key(objectKey),
				RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
	}

	public void deleteBucket(String bucketName) {
		ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder().bucket(bucketName).build();
		ListObjectsV2Response listObjectsResponse;
		do {
			listObjectsResponse = this.s3Client.listObjectsV2(listObjectsRequest);
			List<S3Object> objects = listObjectsResponse.contents();
			for (S3Object object : objects) {
				DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
					.bucket(bucketName)
					.key(object.key())
					.build();
				logger.info("Deleting object bucketName={} object={}", bucketName, object.key());
				this.s3Client.deleteObject(deleteObjectRequest);
				logger.info("Deleted object bucketName={} object={}", bucketName, object.key());
			}
			listObjectsRequest = listObjectsRequest.toBuilder()
				.continuationToken(listObjectsResponse.nextContinuationToken())
				.build();
		}
		while (listObjectsResponse.isTruncated());
		DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
		logger.info("Deleting bucket bucketName={}", bucketName);
		this.s3Client.deleteBucket(deleteBucketRequest);
		logger.info("Deleted bucket bucketName={}", bucketName);
	}

	public void deleteBucketByInstanceId(String instanceId) {
		this.findBucketByInstanceId(instanceId).ifPresent(bucket -> this.deleteBucket(bucket.name()));
	}

	public Optional<Bucket> findBucketByInstanceId(String instanceId) {
		ListBucketsResponse response = this.s3Client.listBuckets();
		return response.buckets().stream().filter(bucket -> {
			String bucketName = bucket.name();
			return this.listBucketTags(bucketName)
				.stream()
				.anyMatch(tag -> tag.key().equals("instance_id") && tag.value().equals(instanceId));
		}).findAny();
	}

	public String buildTrustPolicyForBucket(String bucketName) {
		return """
				{
				    "Version": "2012-10-17",
				    "Statement": [
				        {
				            "Effect": "Allow",
				            "Action": "s3:ListAllMyBuckets",
				            "Resource": "arn:aws:s3:::*"
				        },
				        {
				            "Effect": "Allow",
				            "Action": [
				                "s3:ListBucket",
				                "s3:ListBucketVersions",
				                "s3:ListBucketMultipartUploads",
				                "s3:GetAccelerateConfiguration",
				                "s3:PutAccelerateConfiguration",
				                "s3:GetBucketAcl",
				                "s3:PutBucketAcl",
				                "s3:GetBucketCORS",
				                "s3:PutBucketCORS",
				                "s3:GetBucketVersioning",
				                "s3:PutBucketVersioning",
				                "s3:GetBucketRequestPayment",
				                "s3:PutBucketRequestPayment",
				                "s3:GetBucketLocation",
				                "s3:GetBucketPolicy",
				                "s3:DeleteBucketPolicy",
				                "s3:PutBucketPolicy",
				                "s3:GetBucketNotification",
				                "s3:PutBucketNotification",
				                "s3:GetBucketLogging",
				                "s3:PutBucketLogging",
				                "s3:GetBucketTagging",
				                "s3:PutBucketTagging",
				                "s3:GetBucketWebsite",
				                "s3:PutBucketWebsite",
				                "s3:DeleteBucketWebsite",
				                "s3:GetLifecycleConfiguration",
				                "s3:PutLifecycleConfiguration",
				                "s3:PutReplicationConfiguration",
				                "s3:GetReplicationConfiguration",
				                "s3:DeleteReplicationConfiguration"
				            ],
				            "Resource": "arn:aws:s3:::%s"
				        },
				        {
				            "Effect": "Allow",
				            "Action": [
				                "s3:GetObject",
				                "s3:GetObjectVersion",
				                "s3:PutObject",
				                "s3:GetObjectAcl",
				                "s3:GetObjectVersionAcl",
				                "s3:PutObjectAcl",
				                "s3:PutObjectVersionAcl",
				                "s3:DeleteObject",
				                "s3:DeleteObjectVersion",
				                "s3:ListMultipartUploadParts",
				                "s3:AbortMultipartUpload",
				                "s3:GetObjectTorrent",
				                "s3:GetObjectVersionTorrent",
				                "s3:RestoreObject",
				                "s3:PutObjectTagging",
				                "s3:PutObjectVersionTagging",
				                "s3:GetObjectTagging",
				                "s3:GetObjectVersionTagging",
				                "s3:DeleteObjectTagging",
				                "s3:DeleteObjectVersionTagging"
				            ],
				            "Resource": "arn:aws:s3:::%s/*"
				        }
				    ]
				}
				""".formatted(bucketName, bucketName);
	}

}
