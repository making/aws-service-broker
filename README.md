# AWS Service Broker

## How to deploy the service broker 

[CF Identity Token Service](https://github.com/making/cf-identity-token-service) is required to deploy AWS Service Broker using IAM Role

### Create IAM Role for the service broker

```
CITS_DOMAIN=cits.apps.dhaka.cf-app.com (for example)
OIDC_PROVIDER_ARN=$(aws iam list-open-id-connect-providers --query "OpenIDConnectProviderList[?ends_with(Arn, '$CITS_DOMAIN')].Arn" --output text)

# current org/space name
ORG_NAME=$(cat ~/.cf/config.json | jq -r .OrganizationFields.Name)
SPACE_NAME=$(cat ~/.cf/config.json | jq -r .SpaceFields.Name)

ORG_GUID=$(cf org $ORG_NAME --guid)
SPACE_GUID=$(cf space $SPACE_NAME --guid)

cat << EOF > cf-${ORG_NAME}-${SPACE_NAME}-trust-policy.json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Federated": "${OIDC_PROVIDER_ARN}"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringLike": {
                    "${CITS_DOMAIN}:sub": "${ORG_GUID}:${SPACE_GUID}:*",
                    "${CITS_DOMAIN}:aud": "sts.amazonaws.com"
                }
            }
        }
    ]
}
EOF

aws iam create-role --role-name aws-service-broker --assume-role-policy-document file://cf-${ORG_NAME}-${SPACE_NAME}-trust-policy.json
```

```
sed "s/CHANGE_ME/$(aws sts get-caller-identity --output text --query Account)/" policy/iam-policy.json > iam-policy.json
aws iam put-role-policy --role-name aws-service-broker --policy-name aws-service-broker --policy-document file://iam-policy.json
```

```
ROLE_ARN=$(aws iam get-role --role-name aws-service-broker --query 'Role.Arn' --output text)
```

### Deploy the service broker

```
./mvnw clean package
```

```yaml
cat <<EOF > vars.yaml
ROLE_ARN: ${ROLE_ARN}
CITS_DOMAIN: ${CITS_DOMAIN}
OIDC_PROVIDER_ARN: ${OIDC_PROVIDER_ARN}
EOF
```

```
cf push --vars-file vars.yaml
```

## How to register the service broker

```
cf create-service-broker aws-service-broker admin password <url>
cf enable-service-access iam-role
cf enable-service-access s3
cf enable-service-access dynamodb
```

## How to create the service instance / service binding

### Create a IAM Role

```
cf create-service iam-role free demo
cf create-service-key demo test
cf service-key demo test
```

```json
{
  "credentials": {
    "role_arn": "arn:aws:iam::<ACCOUNT_ID>:role/cf-role/<ROLE_NAME>",
    "role_name": "<ROLE_NAME>"
  }
}
```

### Create a S3 bucket

```
cf create-service s3 free my-bucket -c '{"role_name": "<ROLE_NAME>"}'
cf create-service-key my-bucket test
cf service-key my-bucket test
```

```json
{
  "credentials": {
    "bucket_name": "cf-ff37fff3a7dc42b4853580de4d4d351f",
    "region": "ap-northeast-1",
    "role_arn": "arn:aws:iam::<ACCOUNT_ID>:role/cf-role/<ROLE_NAME>",
    "role_name": "<ROLE_NAME>"
  }
}
```

### Create IAM Policy for DynamoDB

```
cf create-service dynamodb free my-dynamo -c '{"role_name": "<ROLE_NAME>"}'
cf create-service-key my-dynamo test
cf service-key my-dynamo test
```

```json
{
  "credentials": {
    "prefix": "cf-2675c78c666e4a648d90cef684205533",
    "region": "ap-northeast-1",
    "role_arn": "arn:aws:iam::<ACCOUNT_ID>:role/cf-role/<ROLE_NAME>",
    "role_name": "<ROLE_NAME>"
  }
}
```