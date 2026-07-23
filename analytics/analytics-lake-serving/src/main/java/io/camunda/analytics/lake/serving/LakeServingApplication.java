/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving;

import io.camunda.analytics.lake.serving.config.LakeIngestProperties;
import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Serving application over the analytics lake warehouse: a Spring Boot REST API (plus its React UI)
 * backed by an embedded DuckDB instance that queries the Parquet files the lake writer produces.
 * The read layer never writes to the warehouse — ingest, folding and compaction are implemented
 * entirely in {@code analytics-lake} — but this application can HOST that ingest in-process for the
 * one-backend deployment shape: see {@link
 * io.camunda.analytics.lake.serving.ingest.LakeIngestLifecycle} ({@code
 * lake.serving.ingest.enabled}, off by default).
 */
@SpringBootApplication
@EnableConfigurationProperties({LakeServingProperties.class, LakeIngestProperties.class})
public class LakeServingApplication {

  public static void main(final String[] args) {
    SpringApplication.run(LakeServingApplication.class, args);
  }
}
