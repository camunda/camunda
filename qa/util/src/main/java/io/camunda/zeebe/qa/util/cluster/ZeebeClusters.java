/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.cluster;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Registers the {@link ZeebeClusterExtension} extension, which will manage the lifecycle of one or
 * more {@link io.camunda.zeebe.qa.util.cluster.spring.SpringCluster}
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(ZeebeClusterExtension.class)
public @interface ZeebeClusters {

  @Target({ElementType.FIELD})
  @Retention(RetentionPolicy.RUNTIME)
  @Documented
  @Inherited
  @interface ZeebeCluster {
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
  }
}
