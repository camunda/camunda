# Zeebe Go Client


### Testing

To regenerate the gateway mock `mock_pb/mock_gateway.go` run [`mockgen`](https://github.com/golang/mock#installation):

```
mockgen github.com/zeebe-io/zeebe/clients/go/pb GatewayClient,Gateway_ActivateJobsClient > mock_pb/mock_gateway.go
```
