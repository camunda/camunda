/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster.junit;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneCluster;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Registers the {@link ManageTestNodeExtension} extension, which will manage the lifecycle of one or
 * more {@link TestStandaloneCluster}
 *
 * <pre>{@code
 * &#64;ZeebeClusters
 * final class MyClusteredTest {
 *   &#64;ZeebeCluster(autoStart = true, awaitCompleteTopology = true)
 *   private SpringCluster cluster = SpringCluster.builder()
 *          .withBrokersCount(3)
 *          .withReplicationFactor(3)
 *          .withPartitionsCount(1)
 *          .useEmbeddedGateway(true)
 *          .build();
 *
 *   &#64;Test
 *   void shouldConnectToCluster() {
 *     // given
 *     final Topology topology;
 *
 *     // when
 *     try (final ZeebeClient client = cluster.newClientBuilder().build()) {
 *       topology = c.newTopologyRequest().send().join();
 *     }
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(3);
 *   }
 * }</pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(ManageTestNodeExtension.class)
public @interface ManageTestNodes {

  @Target({ElementType.FIELD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @Inherited
  @interface TestCluster {
    /**
     * If true (the default), will block and wait until all nodes in the cluster are ready. Does
     * nothing if {@link #autoStart()} is false.
     */
    boolean awaitReady() default true;

    /**
     * If true (the default), the cluster is considered started only if the topology is complete.
     * Does nothing if {@link #autoStart()} is false.
     */
    boolean awaitCompleteTopology() default true;

    /** If true (the default), will automatically start the cluster before tests. */
    boolean autoStart() default true;

    /**
     * If true (the default), will block and wait until all nodes in the cluster are started. Does
     * nothing if {@link #autoStart()} is false.
     */
    boolean awaitStarted() default true;
  }

  @Target({ElementType.FIELD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @Inherited
  @interface TestNode {
    /**
     * If true (the default), will block and wait until the node is ready. Does nothing if {@link
     * #autoStart()} is false.
     */
    boolean awaitReady() default true;

    /** If true (the default), will automatically start the cluster before tests. */
    boolean autoStart() default true;

    /**
     * If true (the default), will block and wait until the node is started. Does nothing if {@link
     * #autoStart()} is false.
     */
    boolean awaitStarted() default true;

    /**
     * If true (the default), and the annotated node is a standalone broker with an embedded
     * gateway, then the extension will block until the single node cluster is ready to serve
     * request.
     */
    boolean awaitCompleteTopology() default true;
  }
}
