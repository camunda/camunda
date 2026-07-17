## Adding a new operation

Steps 1-9 live in `zeebe/dynamic-config-api` (module `zeebe-cluster-config-api`); step 10 lives in
`zeebe/dynamic-config` (module `zeebe-cluster-config`, which depends on the `-api` module). See
[ADR 0002](adr/0002-extract-cluster-config-api-module.md) for why the split is where it is.

1. **State**: add record to `ClusterConfigurationChangeOperation` sealed interface (in `state/`).
2. **Proto**: add message to `requests.proto` + `topology.proto` if new config field needed.
3. **Serializer**: add encode/decode in `ClusterConfigurationRequestsSerializer` interface (in
   `api/`) + `ProtoBufSerializer` (in `zeebe/dynamic-config`'s `serializer/`).
4. **Request type**: add record to `ClusterConfigurationManagementRequest`.
5. **Topic**: add entry to `ClusterConfigurationRequestTopics`.
6. **Transformer**: new `ConfigurationChangeRequest` impl in `api/` — `operations(ClusterConfiguration)` returns `Either<Exception, List<Op>>`.
7. **Handler**: implement in `ClusterConfigurationManagementRequestsHandler`, delegate to `handleRequest(dryRun, transformer)`.
8. **Sender**: add method in `ClusterConfigurationManagementRequestSender`.
9. **Server**: register `replyTo` handler in `ClusterConfigurationRequestServer.start()`.
10. **Applier** (in `zeebe/dynamic-config`): new `OperationApplier` in `changes/`, register in
    `ConfigurationChangeAppliersImpl`.

