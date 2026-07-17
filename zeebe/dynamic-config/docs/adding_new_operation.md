## Adding a new operation

Steps 1, 2, 3 (interface only), 4, 5 live in `zeebe/dynamic-config-api` (module
`zeebe-cluster-config-api`). Steps 3 (impl), 6, 7, 8, 9, 10 live in `zeebe/dynamic-config` (module
`zeebe-cluster-config`, which depends on the `-api` module). See
[ADR 0002](adr/0002-extract-cluster-config-api-module.md) and its amendment for why the split is
where it is — transformers and the handler dispatch logic are implementation, not the API contract,
so they live in the impl module even though they implement an `-api`-declared interface.

1. **State**: add record to `ClusterConfigurationChangeOperation` sealed interface (in `state/`).
2. **Proto**: add message to `requests.proto` + `topology.proto` if new config field needed.
3. **Serializer**: add encode/decode in `ClusterConfigurationRequestsSerializer` interface (in
   `-api`'s `api/`) + `ProtoBufSerializer` (in `zeebe/dynamic-config`'s `serializer/`).
4. **Request type**: add record to `ClusterConfigurationManagementRequest` (in `-api`'s `api/`).
5. **Topic**: add entry to `ClusterConfigurationRequestTopics` (in `-api`'s `api/`).
6. **Transformer** (in `zeebe/dynamic-config`'s `transformer/`): new `ConfigurationChangeRequest`
   impl — `operations(ClusterConfiguration)` returns `Either<Exception, List<Op>>`.
7. **Handler** (in `zeebe/dynamic-config` root package): implement in
   `ClusterConfigurationManagementRequestsHandler`, delegate to `handleRequest(dryRun, transformer)`.
8. **Sender**: add method in `ClusterConfigurationManagementRequestSender` (in `-api`'s `api/`).
9. **Server**: register `replyTo` handler in `ClusterConfigurationRequestServer.start()` (in
   `-api`'s `api/`).
10. **Applier** (in `zeebe/dynamic-config`): new `OperationApplier` in `changes/`, register in
    `ConfigurationChangeAppliersImpl`.

