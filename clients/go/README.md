# Zeebe Go Client

> [!WARNING]
> The Zeebe Go Client will be officially deprecated with 8.6 release as part of our efforts to streamline the Camunda 8 API experience. This client will not get released with Camunda 8.6, thus no longer receive new features and will be transitioned to a community-maintained status.

### Why This Change?

The decision to deprecate the Go Client aligns with our broader API strategy and resource allocation priorities. The Go Client has seen limited adoption. Moving forward, we are focusing our efforts on the Camunda 8 REST API, which offers a unified, more widely-supported approach for interacting with Zeebe and other Camunda services.

### What Does This Mean for Users?

* No New Features or Updates: Starting with Camunda 8.6, the Go Client will no longer receive new features, updates, or official support from Camunda.
* The official Go client and zbctl will only remain available and maintained for supported minor versions up to Camunda 8.5.
* Community Maintenance: The Go Client will be moved to [Camunda Community Hub](https://github.com/camunda-community-hub) and can be maintained by the community. We encourage contributions from users who wish to continue using and improving this client.
* Transition to REST API: We recommend users transition to the Camunda 8 REST API for all future development. The REST API provides comprehensive functionality and is supported by tools such as cURL, Postman, and OpenAPI.

### Future Considerations

We value feedback from our community. Based on user input, we may explore developing a new client for the Camunda 8 REST API, based on a different technology that aligns with our strategic goals and internal expertise.
For more information on the deprecation and our API strategy, please refer to the official [Camunda blog](https://camunda.com/blog/).

# Development

If we had a gateway-protocol change we need to make sure that we regenerate the protobuf file, which is used by the go client.
In order to do this please follow [this guide](../../gateway-protocol-impl/README.md).

## Testing

### gRPC Mock

To regenerate the gateway mock `internal/mock_pb/mock_gateway.go` run [`mockgen`](https://github.com/golang/mock#installation) from the root of this module (`clients/go`):

```
mockgen -source=pkg/pb/gateway.pb.go GatewayClient,Gateway_ActivateJobsClient > internal/mock_pb/mock_gateway.go
```

### Integration tests

To run the integration tests, a Docker image for Zeebe must be built with the tag 'current-test'.
To do that you can run the following command from the root of this repository:

```
docker build --build-arg DIST=build -t camunda/zeebe:current-test .
```

To add new zbctl tests, you must generate a golden file with the expected output of the command you are testing. The tests ignore numbers so you can leave any keys or timestamps in your golden file, even though these will most likely be different from test command's output. However, non-numeric variables are not ignored. For instance, the help menu contains:

```
--clientCache string    Specify the path to use for the OAuth credentials cache. If omitted, will read from the environment variable 'ZEEBE_CLIENT_CONFIG_PATH' (default "YOUR_HOME/.camunda/credentials")
```

To make them host-independent, the tests replace the HOME environment variable with `/tmp` which means you must do the same in your golden file.

## Dependencies

After making changes to the Go client, you can vendor the new dependencies with:

```
go mod vendor
```

This command will also remove or download dependencies as needed. To do that without vendoring them, you can run `go mod tidy`.
