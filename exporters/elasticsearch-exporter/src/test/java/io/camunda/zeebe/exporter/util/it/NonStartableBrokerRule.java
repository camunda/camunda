/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util.it;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.exporter.AbstractElasticsearchExporterIntegrationTestCase;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This interface is a temporary workaround to prevent actual tests from starting a broker manually.
 * Instead, you should use {@link AbstractElasticsearchExporterIntegrationTestCase#startBroker()}.
 *
 * <p>As all of these utility classes will be removed by the end of Q2-2022, it's an OK trade-off to
 * avoid rewriting most of the tests (which will be rewritten differently anyway).
 */
public interface NonStartableBrokerRule {

  BrokerCfg getBrokerConfig();

  List<ExporterCfg> getConfiguredExporters();

  boolean hasConfiguredExporters();

  <T, E extends Exporter> ExporterIntegrationRule configure(
      String id, Class<E> exporterClass, T configuration);

  <E extends Exporter> ExporterIntegrationRule configure(
      String id, Class<E> exporterClass, Map<String, Object> arguments);

  void performSampleWorkload();

  void visitExportedRecords(Consumer<Record<?>> visitor);

  void deployProcess(BpmnModelInstance process, String filename);

  void deployResourceFromClasspath(String classpathResource);

  long createProcessInstance(String processId, Map<String, Object> variables);

  JobWorker createJobWorker(String type, JobHandler handler);

  void publishMessage(String messageName, String correlationKey);
}
