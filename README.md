# IAM Role Service Broker

## How to deploy the service broker 

[CF Identity Token Service](https://github.com/making/cf-identity-token-service) is required to deploy IAM Role Service Broker using IAM Role

### Create IAM Role for the service broker

```
CITS_DOMAIN=...
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

aws iam create-role --role-name iam-role-service-broker --assume-role-policy-document file://cf-${ORG_NAME}-${SPACE_NAME}-trust-policy.json
```

```
sed "s/CHANGE_ME/$(aws sts get-caller-identity --output text --query Account)/" policy/iam-policy.json > iam-policy.json
aws iam put-role-policy --role-name iam-role-service-broker --policy-name iam-role-service-broker --policy-document file://iam-policy.json
```

```
ROLE_ARN=$(aws iam get-role --role-name iam-role-service-broker --query 'Role.Arn' --output text)
```

### Deploy the service broker

```
./mvnw clean package
```

```
sed -i.bk \
  -e "s|CHANGE_ME_ROLE_ARN|${ROLE_ARN}|" \
  -e "s|CHANGE_ME_CITS_DOMAIN|${CITS_DOMAIN}|" \
  -e "s|CHANGE_ME_OIDC_PROVIDER_ARN|${OIDC_PROVIDER_ARN}|" manifest-template.yml > manifest.yml

cf push
```

## How to register the service broker

```
cf create-service-broker iam-role-service-broker admin password <url>
cf enable-service-access iam-role
```

## How to create the service instance / service binding

```
cf create-service iam-role free demo
cf create-service-key demo test
cf service-key demo test
```