# Mock Service Broker

## How to deploy the service broker 

```
./mvnw clean package
cf push mock-service-broker -p target/mock-service-broker-0.0.1-SNAPSHOT.jar --no-start
cf set-env mock-service-broker JBP_CONFIG_OPEN_JDK_JRE "{ jre: { version: 17.+ } }"
cf start mock-service-broker
```

## How to register the service broker

```
cf create-service-broker mock-service-broker admin password <url>
cf enable-service-access mock
```

## How to create the service instance / service binding

```
cf create-service mock free demo
cf create-service-key demo test
cf service-key demo test
```