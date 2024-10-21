# Demo S3

```
cf create-service s3 free demo-bucket -c '{"role_name": "<ROLE_NAME>"}'
```


```
./mvnw clean package -DskipTests
cf push --var CITS_DOMAIN=cits.apps.dhaka.cf-app.com # CHANGE DOMAIN
```

```
APP_URL=https://$(cf curl /v3/apps/$(cf app demo-s3 --guid)/routes | jq -r ".resources[0].url")
```

```
$ curl ${APP_URL}/persons -H content-type:application/json -d '{"id": 1, "firstName": "John", "lastName": "Doe"}'
$ curl ${APP_URL}/persons/1
{"id":1,"firstName":null,"lastName":"Doe"}
$ curl ${APP_URL}/persons/1 -XDELETE
```