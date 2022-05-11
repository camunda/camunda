/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumSet;
import java.util.stream.Stream;
import org.elasticsearch.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/** Collection of utilities for unit and integration tests. */
final class TestSupport {
  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(RestClient.class.getPackage().getImplementationVersion());

  private TestSupport() {}

  /**
   * Returns an Elasticsearch container pointing at the same version as the {@link RestClient}.
   *
   * <p>The container is configured to use 750m of heap and 750m of direct memory. This is required
   * because Elasticsearch 7.x, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   */
  static ElasticsearchContainer createDefaultContainer() {
    return new ElasticsearchContainer(ELASTIC_IMAGE)
        .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx512m -XX:MaxDirectMemorySize=536870912")
        .withEnv("action.auto_create_index", "true")
        .withEnv("xpack.security.enabled", "false");
  }

  /**
   * Returns a stream of value types which are export-able by the exporter, i.e. the ones with an
   * index template.
   *
   * <p>Issue https://github.com/camunda/zeebe/issues/8337 should fix this and ensure all types have
   * an index template.
   */
  static Stream<ValueType> provideValueTypes() {
    final var excludedValueTypes =
        EnumSet.of(
            ValueType.SBE_UNKNOWN,
            ValueType.NULL_VAL,
            ValueType.TIMER,
            ValueType.PROCESS_INSTANCE_RESULT,
            ValueType.DEPLOYMENT_DISTRIBUTION,
            ValueType.PROCESS_EVENT,
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
    return EnumSet.complementOf(excludedValueTypes).stream();
  }
}
