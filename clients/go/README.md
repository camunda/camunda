# Zeebe Go Client


### Testing

To regenerate the gateway mock `internal/mock_pb/mock_gateway.go` run [`mockgen`](https://github.com/golang/mock#installation):

```
mockgen github.com/zeebe-io/zeebe/clients/go/tools/pb GatewayClient,Gateway_ActivateJobsClient > internal/mock_pb/mock_gateway.go
```
