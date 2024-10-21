# Demo DynamoDB

```
cf create-service dynamodb free demo-dynamodb -c '{"role_name": "<ROLE_NAME>"}'
```

```
./mvnw clean package -DskipTests
cf push --var CITS_DOMAIN=cits.apps.dhaka.cf-app.com # CHANGE DOMAIN
```

```
APP_URL=https://$(cf curl /v3/apps/$(cf app demo-dynamodb --guid)/routes | jq -r ".resources[0].url")
```

```
curl ${APP_URL}/movies -H "Content-Type: application/json" -d '{"title":"Inception","releaseYear":2010,"genre":"Science Fiction","rating":8.8,"director":"Christopher Nolan"}'
curl ${APP_URL}/movies -H "Content-Type: application/json" -d '{"title":"The Matrix","releaseYear":1999,"genre":"Action","rating":8.7,"director":"The Wachowskis"}'
curl ${APP_URL}/movies -H "Content-Type: application/json" -d '{"title":"Interstellar","releaseYear":2014,"genre":"Adventure","rating":8.6,"director":"Christopher Nolan"}'
```

```
curl -s ${APP_URL}/movies | jq .
```

```
curl -s "${APP_URL}/movies?genre=Action" | jq .
```