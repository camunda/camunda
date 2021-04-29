To run the upgradability tests, a Docker image for Zeebe must be built with the tag 'current-test'. To do that you can run (in the zeebe-io/zeebe dir):

```
docker build --build-arg DISTBALL=dist/target/camunda-cloud-zeebe*.tar.gz -t camunda/zeebe:current-test --target app .
```

