# pm-tasks Summary

**Timestamp:** 2026-03-19T07:32:02.971Z

## Decomposed Tasks

1. Create event-bridge module structure with 4 sub-modules
2. Define SBE protocol schemas (11 message types) (depends on: 1)
3. Configure SBE code generation in event-bridge-core (depends on: 2)
4. Implement EventRecordValue shim for raw bytes support (depends on: 3)
5. Implement EventBridgeLogStorage (RAFT commit → LogStream bridge) (depends on: 3)
6. Bootstrap RAFT partitions via RaftPartitionFactory pattern (depends on: 5)
7. Implement PublishActor (event sequencing with Sequencer) (depends on: 4, 6)
8. Implement PollActor (LogStreamReader with long-poll support) (depends on: 6)
9. Implement OffsetStore (offset tracking per group/consumer/partition) (depends on: 3)
10. Implement SnapshotManager (offset serialization to RAFT snapshots) (depends on: 9)
11. Implement ConsumerGroupCoordinator (subscribe, rebalance, heartbeat timeout) (depends on: 9)
12. Implement stable round-robin assignment algorithm (depends on: 11)
13. Implement truncation logic (min committed offset across live consumers) (depends on: 10, 11)
14. Implement TopologyBroadcaster (leader state to SWIM metadata) (depends on: 6)
15. Wire broker components together with ActorScheduler (depends on: 7, 8, 11, 13, 14)
16. Implement TopologyService (SWIM gossip consumption for leader discovery) (depends on: 3)
17. Implement BrokerRequestRouter (4-attempt retry via NettyMessagingService) (depends on: 3, 16)
18. [FRONTEND] Create Spring MVC controllers (publish, poll, commit, subscribe, heartbeat) (depends on: 17)
19. Implement GlobalExceptionHandler (HTTP error mapping) (depends on: 18)
20. Implement EventBridgeClient (Java client API) (depends on: 3)
21. Implement Consumer handle (position tracking, rebalance semantics) (depends on: 20)
22. Implement StandaloneEventBridge (gateway + broker in single JVM) (depends on: 15, 18)
23. Register StandaloneEventBridge in dist/pom.xml (depends on: 22)
24. Add event-bridge to root pom.xml reactor (depends on: 1)
25. Unit tests for broker components (actors, coordinator, offset store) (depends on: 15)
26. Unit tests for gateway components (router, topology service) (depends on: 17)
27. Unit tests for client library (depends on: 21)
28. Integration test (StandaloneEventBridgeIT) (depends on: 23)
