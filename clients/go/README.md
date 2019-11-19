# Zeebe Go Client


### Testing

To regenerate the gateway mock `internal/mock_pb/mock_gateway.go` run [`mockgen`](https://github.com/golang/mock#installation):

```
mockgen github.com/zeebe-io/zeebe/clients/go/tools/pb GatewayClient,Gateway_ActivateJobsClient > internal/mock_pb/mock_gateway.go
```

### Dependencies

After making changes to the Go client, you can vendor the new dependencies with:

```
go mod vendor
```

This command will also remove or download dependencies as needed. To do that without vendoring them you can run `go mod tidy`.
