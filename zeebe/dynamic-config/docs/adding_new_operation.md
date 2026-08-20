## Adding a new operation

1. **State**: add record to `ClusterConfigurationChangeOperation` sealed interface (in `state/`).
2. **Proto**: add message to `requests.proto` + `topology.proto` if new config field needed.
3. **Serializer**: add encode/decode in `ClusterConfigurationRequestsSerializer` interface + `ProtoBufSerializer`.
4. **Request type**: add record to `ClusterConfigurationManagementRequest`.
5. **Topic**: add entry to `ClusterConfigurationRequestTopics`.
6. **Transformer**: new `ConfigurationChangeRequest` impl in `api/` — `operations(ClusterConfiguration)` returns `Either<Exception, List<Op>>`.
7. **Handler**: implement in `ClusterConfigurationManagementRequestsHandler`, delegate to `handleRequest(dryRun, transformer)`.
8. **Sender**: add method in `ClusterConfigurationManagementRequestSender`.
9. **Server**: register `replyTo` handler in `ClusterConfigurationRequestServer.start()`.
10. **Applier**: new `OperationApplier` in `changes/`, register in `ConfigurationChangeAppliersImpl`.

