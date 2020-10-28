/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.metrics;

import io.prometheus.client.Counter;

public class FNIMetrics {

  private static final Counter FLOW_NODE_INSTANCE_COUNTER =
      Counter.build()
          .namespace("zeebe")
          .name("flow_node_instance_counter")
          .help("Count flow node instances, per partition and type (completed and terminated).")
          .labelNames("partition", "type")
          .register();

  public static void addFlowNodeInstanceCompleted(final int partitionId) {
    FLOW_NODE_INSTANCE_COUNTER.labels(Integer.toString(partitionId), "completed").inc();
  }

  public static void addFlowNodeInstanceTerminated(final int partitionId) {
    FLOW_NODE_INSTANCE_COUNTER.labels(Integer.toString(partitionId), "terminated").inc();
  }
}
