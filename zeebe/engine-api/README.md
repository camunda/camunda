# Workflow engine API

In-process API between the broker and the workflow engine. The broker
compiles against this module only. It never depends on the engine at
compile scope.

It holds:

- `EngineFactory`: the interface through which the broker creates the
  engine, runs state migrations and reads or changes engine state.
- `EngineDependencies`, `MigrationStatusReader`: the types that cross
  this boundary.
- Configuration types that the broker and `configuration` need.

The `zeebe/engine` module implements `EngineFactory`
(`DefaultEngineFactory`). The `dist` module depends on the engine and
passes the implementation to the broker. Broker tests depend on the
engine at test scope and create the implementation directly.

This is not the engine's external API. That stays the records in
`zeebe-protocol`.
