# Zeebe Go Client


### Testing

To regenerate the gateway mock `internal/mock_pb/mock_gateway.go` run [`mockgen`](https://github.com/golang/mock#installation):

```
mockgen github.com/zeebe-io/zeebe/clients/go/pkg/pb GatewayClient,Gateway_ActivateJobsClient > internal/mock_pb/mock_gateway.go
```

To run the integration tests, a Docker image for Zeebe must be built with the tag 'current-test'. To do that you can run:
```
docker build --build-arg DISTBALL=dist/target/zeebe-distribution*.tar.gz  -t camunda/zeebe:current-test .
```

### Dependencies

After making changes to the Go client, you can vendor the new dependencies with:

```
go mod vendor
```

This command will also remove or download dependencies as needed. To do that without vendoring them you can run `go mod tidy`.
