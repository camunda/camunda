/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal analytics webapp: a read/serving layer over the H2 dataset the analytics pipeline
 * produces, plus a static UI to declare a dataset, build a report on it, and view it. First,
 * intentionally-rough version — point {@code analytics.dataset.url} at the pipeline's H2 (server
 * mode) to see live data, or run standalone against an empty/seeded H2.
 */
@SpringBootApplication
public class AnalyticsWebappApplication {

  public static void main(final String[] args) {
    SpringApplication.run(AnalyticsWebappApplication.class, args);
  }
}
