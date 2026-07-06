/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the hand-rolled JSON index mappings from the classpath (mirroring OC's schema-manager index
 * schema files). The control-plane indices are fixed, so their mappings are authored as JSON
 * resources rather than derived.
 */
final class DocumentMappings {

  static final String DATASET_SPEC = "mappings/analytics-dataset-spec.json";
  static final String REPORT_SPEC = "mappings/analytics-report-spec.json";
  static final String METER_ID = "mappings/analytics-meter-id.json";

  private DocumentMappings() {}

  static String load(final String resource) {
    try (InputStream in = DocumentMappings.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("missing index mapping resource: " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException("failed to load index mapping " + resource, e);
    }
  }
}
