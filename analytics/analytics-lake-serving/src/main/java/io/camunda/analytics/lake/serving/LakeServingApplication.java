/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Read-only serving application over the analytics lake warehouse: a Spring Boot REST API (plus,
 * once built, its React UI) backed by an embedded DuckDB instance that queries the Parquet files
 * the lake writer produces. This module never writes to the warehouse — ingest, folding and
 * compaction live entirely in {@code analytics-lake}; this is scaffolding only, the explain-shaped
 * read features land in follow-up lanes on top of this structure.
 */
@SpringBootApplication
@EnableConfigurationProperties(LakeServingProperties.class)
public class LakeServingApplication {

  public static void main(final String[] args) {
    SpringApplication.run(LakeServingApplication.class, args);
  }
}
