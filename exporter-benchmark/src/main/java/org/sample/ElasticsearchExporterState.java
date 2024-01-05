/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@State(Scope.Thread)
public class ElasticsearchExporterState extends ExporterState {

  @Override
  protected Exporter buildExporter(Controller controller, ElasticsearchContainer elasticsearch)
      throws Exception {

    ElasticsearchExporterConfiguration config = new ElasticsearchExporterConfiguration();
    config.url = elasticsearch.getHttpHostAddress();

    Exporter exporter = new ElasticsearchExporter();

    exporter.configure(
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
    exporter.open(controller);

    return exporter;
  }

  @Setup(Level.Iteration)
  public void setUpIteration() throws Exception {

    super.setUpIteration();
  }

  @TearDown(Level.Iteration)
  public void tearDownIteration() {
    super.tearDownIteration();
  }

  @Setup(Level.Trial)
  public void setUpElasticsearch() {
    super.setUpElasticsearch();
  }

  @TearDown(Level.Trial)
  public void tearDownElasticsearch() {
    super.tearDownElasticsearch();
  }
}
