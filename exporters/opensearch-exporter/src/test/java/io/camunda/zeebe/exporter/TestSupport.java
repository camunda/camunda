/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.RecordType;
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
   * <p>The container is configured to use 512m of heap and 512m of direct memory. This is required
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
   * Sets the correct indexing configuration field for the given value type. This is particularly
   * helpful for parameterized tests.
   *
   * <p>TODO: this is terrible, but the configuration is also terrible to use programmatically
   */
  @SuppressWarnings("checkstyle:innerassignment")
  static void setIndexingForValueType(
      final IndexConfiguration config, final ValueType valueType, final boolean value) {
    switch (valueType) {
      case JOB -> config.job = value;
      case DEPLOYMENT -> config.deployment = value;
      case PROCESS_INSTANCE -> config.processInstance = value;
      case INCIDENT -> config.incident = value;
      case MESSAGE -> config.message = value;
      case MESSAGE_SUBSCRIPTION -> config.messageSubscription = value;
      case PROCESS_MESSAGE_SUBSCRIPTION -> config.processMessageSubscription = value;
      case JOB_BATCH -> config.jobBatch = value;
      case VARIABLE -> config.variable = value;
      case VARIABLE_DOCUMENT -> config.variableDocument = value;
      case PROCESS_INSTANCE_CREATION -> config.processInstanceCreation = value;
      case PROCESS_INSTANCE_MODIFICATION -> config.processInstanceModification = value;
      case ERROR -> config.error = value;
      case PROCESS -> config.process = value;
      case DECISION -> config.decision = value;
      case DECISION_REQUIREMENTS -> config.decisionRequirements = value;
      case DECISION_EVALUATION -> config.decisionEvaluation = value;
      case CHECKPOINT -> config.checkpoint = value;
      case TIMER -> config.timer = value;
      case MESSAGE_START_EVENT_SUBSCRIPTION -> config.messageStartEventSubscription = value;
      case PROCESS_EVENT -> config.processEvent = value;
      case DEPLOYMENT_DISTRIBUTION -> config.deploymentDistribution = value;
      case ESCALATION -> config.escalation = value;
      case SIGNAL -> config.signal = value;
      case SIGNAL_SUBSCRIPTION -> config.signalSubscription = value;
      case RESOURCE_DELETION -> config.resourceDeletion = value;
      case COMMAND_DISTRIBUTION -> config.commandDistribution = value;
      default -> throw new IllegalArgumentException(
          "No known indexing configuration option for value type " + valueType);
    }
  }

  /**
   * Sets the correct indexing configuration field for the given record type. This is particularly
   * helpful for parameterized tests.
   */
  @SuppressWarnings("checkstyle:innerassignment")
  static void setIndexingForRecordType(
      final IndexConfiguration config, final RecordType recordType, final boolean value) {
    switch (recordType) {
      case EVENT -> config.event = value;
      case COMMAND -> config.command = value;
      case COMMAND_REJECTION -> config.rejection = value;
      default -> throw new IllegalArgumentException(
          "No known indexing configuration option for record type " + recordType);
    }
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
        EnumSet.of(ValueType.SBE_UNKNOWN, ValueType.NULL_VAL, ValueType.PROCESS_INSTANCE_RESULT);
    return EnumSet.complementOf(excludedValueTypes).stream();
  }
}
