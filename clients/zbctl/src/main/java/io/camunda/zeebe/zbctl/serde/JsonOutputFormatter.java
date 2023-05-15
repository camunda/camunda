/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.serde;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import java.io.IOException;
import java.io.OutputStream;

public final class JsonOutputFormatter implements OutputFormatter {
  private final JsonFactory factory = new JsonFactory();

  @Override
  public void write(final OutputStream output, final Topology topology) throws IOException {
    try (final var generator = factory.createGenerator(output, JsonEncoding.UTF8)) {
      writeJson(generator, topology);
    }
  }

  private void writeJson(final JsonGenerator generator, final Topology topology)
      throws IOException {
    generator.writeStartObject();
    generator.writeStringField("version", topology.getGatewayVersion());
    generator.writeNumberField("clusterSize", topology.getClusterSize());
    generator.writeNumberField("partitionsCount", topology.getPartitionsCount());
    generator.writeNumberField("replicationFactor", topology.getReplicationFactor());
    generator.writeArrayFieldStart("brokers");
    for (final var broker : topology.getBrokers()) {
      writeJson(generator, broker);
    }
    generator.writeEndArray();
    generator.writeEndObject();
  }

  private void writeJson(final JsonGenerator generator, final BrokerInfo broker)
      throws IOException {
    generator.writeStartObject();

    generator.writeNumberField("nodeId", broker.getNodeId());
    generator.writeStringField("host", broker.getHost());
    generator.writeNumberField("port", broker.getPort());
    generator.writeStringField("address", broker.getAddress());
    generator.writeStringField("version", broker.getVersion());
    generator.writeArrayFieldStart("partitions");
    for (final var partition : broker.getPartitions()) {
      writeJson(generator, partition);
    }
    generator.writeEndArray();
    generator.writeEndObject();
  }

  private void writeJson(final JsonGenerator generator, final PartitionInfo partitionInfo)
      throws IOException {
    generator.writeStartObject();

    generator.writeNumberField("partitionId", partitionInfo.getPartitionId());
    generator.writeStringField("role", partitionInfo.getRole().name());
    generator.writeBooleanField("isLeader", partitionInfo.isLeader());
    generator.writeStringField("health", partitionInfo.getHealth().name());
    generator.writeEndObject();
  }
}
