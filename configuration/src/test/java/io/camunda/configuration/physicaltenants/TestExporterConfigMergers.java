/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only {@link ExporterConfigMerger}s registered via {@code
 * src/test/resources/META-INF/services} so the resolver's {@code ServiceLoader} discovery and the
 * decision table of ADR-0008 §2 can be exercised without depending on the real ES/OS exporter
 * modules (which {@code configuration/} deliberately does not see — real-merger coverage lives in
 * the end-to-end {@code PhysicalTenantExporterConfigIT}).
 */
final class TestExporterConfigMergers {

  /** Exporter class name with exactly one merger — the merge path of the decision table. */
  static final String MERGEABLE_CLASS = "io.camunda.configuration.test.MergeableExporter";

  /** Exporter class name whose merger always fails — exercises the error wrapping. */
  static final String FAILING_CLASS = "io.camunda.configuration.test.FailingMergeExporter";

  /** Exporter class name claimed by two mergers — must fail startup. */
  static final String DUPLICATE_CLAIMED_CLASS =
      "io.camunda.configuration.test.DuplicateClaimedExporter";

  /** Marker key the recording merger adds so tests can prove the merger ran. */
  static final String MERGED_BY_KEY = "mergedby";

  static final String MERGED_BY_VALUE = "test-merger";

  private TestExporterConfigMergers() {}

  public static final class RecordingMerger implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return MERGEABLE_CLASS.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      // top-level merge, tenant wins — plus a marker so tests can tell merger output apart from
      // anything the plain binder could have produced
      final Map<String, Object> merged = new LinkedHashMap<>(rootArgs);
      merged.putAll(tenantArgs);
      merged.put(MERGED_BY_KEY, MERGED_BY_VALUE);
      return merged;
    }
  }

  public static final class FailingMerger implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return FAILING_CLASS.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      throw new IllegalStateException("intentional test merge failure");
    }
  }

  public static final class FirstDuplicateClaimant implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return DUPLICATE_CLAIMED_CLASS.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      return tenantArgs;
    }
  }

  public static final class SecondDuplicateClaimant implements ExporterConfigMerger {

    @Override
    public boolean supports(final String className) {
      return DUPLICATE_CLAIMED_CLASS.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      return tenantArgs;
    }
  }
}
