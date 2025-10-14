#!/usr/bin/bash
set -euo pipefail
if [[ -n "$1" ]]; then
        ECS_VERSION="8.9.0.$1"
else
        ECS_VERSION="latest"
fi

./mvnw clean package -DskipChecks -DskipTests -PskipFrontendBuild -pl dist -am

docker buildx build --target app -t "camunda/camunda-ecs" --build-arg DISTBALL=dist/target/camunda-zeebe-*.tar.gz .

IMAGE="registry.camunda.cloud/team-zeebe/camunda-ecs:$ECS_VERSION"
docker tag camunda/camunda-ecs $IMAGE
docker push $IMAGE
