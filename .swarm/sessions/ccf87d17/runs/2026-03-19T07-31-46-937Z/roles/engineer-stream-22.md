# engineer-stream-22 Summary

**Timestamp:** 2026-03-19T13:23:48.484Z

## Changes

- `event-bridge/event-bridge-core/src/main/java/io/camunda/eventbridge/core/config/EventBridgeProperties.java`: Added `ClusterProperties` nested record (name, nodeId, bindHost, bindPort, advertisedHost, advertisedPort, initialContactPoints) with standalone defaults; added `cluster` field to the top-level record
- `dist/src/main/java/io/camunda/application/EventBridgeModuleConfiguration.java`: **New file** — self-contained `@Configuration` providing `ActorScheduler` and `AtomixCluster` beans; carries `@EnableAutoConfiguration` for Spring MVC/Tomcat/Jackson; builds `ClusterConfig` from `EventBridgeProperties.ClusterProperties`
- `dist/src/main/java/io/camunda/application/StandaloneEventBridge.java`: Replaced `CommonsModuleConfiguration` with `EventBridgeModuleConfiguration` as the bootstrap source
- `event-bridge-broker` test files (×5): Added `null` 8th argument to `EventBridgeProperties` constructor calls

## Verification

- Build: ✅ (all 4 modules compiled clean)
- Tests: ✅ (38/38 pass)
- Lint: ✅ (Spotless reported no changes needed)

## Notes

`CommonsModuleConfiguration` was excluded because it transitively pulls in `ActorClockControlledPropertiesOverride`, which has `@DependsOn("unifiedConfigurationHelper")` — a bean only available in the full Camunda unified-config context. `EventBridgeModuleConfiguration` provides the same two infrastructure beans (`ActorScheduler`, `AtomixCluster`) directly, keeping the event bridge a lightweight standalone component with no dependency on Camunda's unified configuration system.
