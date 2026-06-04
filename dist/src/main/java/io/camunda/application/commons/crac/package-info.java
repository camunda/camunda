/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * CRaC (Coordinated Restore at Checkpoint) enablement for the Camunda distribution.
 *
 * <h2>Why</h2>
 *
 * Restoring a pre-warmed JVM image lets a Camunda process start in well under a second instead of
 * paying full Spring + class-loading + JIT warm-up on every boot. The primary driver is CI, where
 * the same distribution is started hundreds of times per day, but the same mechanism benefits any
 * fast-scaling deployment.
 *
 * <h2>What this package provides (foundation)</h2>
 *
 * <ul>
 *   <li>{@link io.camunda.application.commons.crac.CracCheckpointConfiguration} — an opt-in ({@code
 *       camunda.crac.enabled=true}) registration point that wires every {@link org.crac.Resource}
 *       bean into the global CRaC context.
 *   <li>The {@code org.crac:crac} API facade on the runtime classpath (see {@code dist/pom.xml}) so
 *       Spring's checkpoint-on-refresh can initialize.
 *   <li>An opt-in CRaC JDK base image in {@code camunda.Dockerfile} ({@code --build-arg
 *       BASE=crac}).
 * </ul>
 *
 * <h2>What still needs doing (per-component follow-up)</h2>
 *
 * A CRaC checkpoint aborts if the process holds open file descriptors that are not released first.
 * A spike against {@code StandaloneOperate} confirmed the JVM/Spring path works, then failed with
 * {@code CheckpointOpenSocketException} / {@code CheckpointOpenResourceException} on live
 * Elasticsearch and Zeebe sockets and netty {@code epoll}/{@code eventfd} descriptors. Each owner
 * below must contribute a {@link org.crac.Resource} bean that closes its resource in {@link
 * org.crac.Resource#beforeCheckpoint(org.crac.Context)} and reopens it in {@link
 * org.crac.Resource#afterRestore(org.crac.Context)}:
 *
 * <ul>
 *   <li><b>Search clients</b> (ES/OpenSearch HTTP sockets) — {@code
 *       io.camunda.search.connect.tenant.SearchClients} (already {@code AutoCloseable}); beans in
 *       {@code commons.search} ({@code PhysicalTenantSearchClientReadersConfiguration}, {@code
 *       NativeSearchClientsConfiguration}) and {@code operate.connect.ElasticsearchConnector}.
 *   <li><b>Cluster transport</b> (netty {@code epoll}/{@code eventfd}) — {@code
 *       commons.clustering.AtomixClusterConfiguration} ({@code AtomixCluster}, which owns {@code
 *       NettyMessagingService}'s event-loop groups).
 *   <li><b>Broker client</b> — {@code commons.broker.client.BrokerClientConfiguration}.
 *   <li><b>Zeebe/Camunda gRPC client</b> — the {@code ManagedChannel} in {@code
 *       io.camunda.client.impl.CamundaClientImpl}, where used as a bean.
 * </ul>
 *
 * <p>Spring auto-manages {@code SmartLifecycle} beans across checkpoint; the resources above are
 * not {@code SmartLifecycle}, which is why they need explicit {@link org.crac.Resource} handlers.
 */
package io.camunda.application.commons.crac;
