/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;

public abstract class ExporterState {

  private ElasticsearchContainer elasticSearch;

  private List<String> recordsTemplate;
  private Exporter exporter;
  private long keyCounter = 1;

  public void setUpTrial() {
    setUpElasticsearch();
  }

  public void setUpIteration() throws Exception {
    setUpExporter();
    loadProcessInstanceRecordsTemplate();
  }

  public void tearDownIteration() {
    tearDownExporter();
  }

  public void tearDownTrial() {
    tearDownElasticsearch();
  }

  public void setUpExporter() throws Exception {

    ExporterTestController controller = new ExporterTestController();

    exporter = buildExporter(controller, elasticSearch);

    List<String> deploymentEvents = loadRecordsTemplate("deployment-event-log.json");
    List<Record<?>> deploymentRecords = buildRecords(deploymentEvents, r -> r);

    deploymentRecords.forEach(exporter::export);
  }

  protected abstract Exporter buildExporter(
      Controller controller, ElasticsearchContainer elasticsearch) throws Exception;

  public void tearDownExporter() {
    exporter.close();
  }

  public void loadProcessInstanceRecordsTemplate() throws IOException {

    recordsTemplate = loadRecordsTemplate("process-instance-event-log.json");
  }

  private List<String> loadRecordsTemplate(String source) throws IOException {
    try (InputStream inputStream =
        OperateElasticsearchExporterState.class.getClassLoader().getResourceAsStream(source)) {

      return IOUtils.readLines(inputStream, StandardCharsets.UTF_8);
    }
  }

  public Exporter getExporter() {
    return exporter;
  }

  public void setUpElasticsearch() {
    elasticSearch =
        // copied from exporter tests
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.7.1")
            .withClasspathResourceMapping(
                "elasticsearch-fast-startup.options",
                "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
                BindMode.READ_ONLY)
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("action.auto_create_index", "true")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.watcher.enabled", "false")
            .withEnv("xpack.ml.enabled", "false");
    elasticSearch.start();
  }

  public void tearDownElasticsearch() {
    elasticSearch.stop();
  }

  public List<Record<?>> buildProcessInstanceRecords() {
    return buildRecords(
        recordsTemplate,
        r ->
            r.replaceAll("@processInstanceKey", Long.toString(keyCounter++))
                .replaceAll("@processInstanceCreationKey", Long.toString(keyCounter++))
                .replaceAll("@startEventKey", Long.toString(keyCounter++))
                .replaceAll("@serviceTaskKey", Long.toString(keyCounter++))
                .replaceAll("@endEventKey", Long.toString(keyCounter++))
                .replaceAll("@jobKey", Long.toString(keyCounter++))
                .replaceAll("@sequenceFlow1Key", Long.toString(keyCounter++))
                .replaceAll("@jobBatchKey", Long.toString(keyCounter++))
                .replaceAll("@sequenceFlow2Key", Long.toString(keyCounter++))
                .replaceAll("@processEventKey", Long.toString(keyCounter++)));
  }

  private List<Record<?>> buildRecords(
      List<String> source, Function<String, String> sourceModifier) {
    final List<Record<?>> result = new ArrayList<>();

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new ZeebeProtocolModule());

    for (String recordTemplate : source) {
      recordTemplate = sourceModifier.apply(recordTemplate);

      Record<?> record;
      try {
        record = objectMapper.readValue(recordTemplate, new TypeReference<Record<?>>() {});
      } catch (IOException e) {
        System.out.println(recordTemplate);
        throw new RuntimeException(e);
      }
      result.add(record);
    }

    return result;
  }
}
