/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.List;

public interface IndexTemplateDescriptor extends IndexDescriptor {

  String PARTITION_ID = "partitionId";
  String POSITION = "position";

  String getIndexPattern();

  String getTemplateName();

  List<String> getComposedOf();

  /**
   * Whether this template's schema JSON declares a settings block beyond the ones runtime
   * configuration owns (shards/replicas/refresh_interval/priority) — e.g. a custom {@code analysis}
   * block. The search engine normalizes such a block when it stores it (injecting defaults,
   * relocating keys, coercing scalar values to lists), so a raw JSON-vs-stored byte-for-byte
   * comparison of it can never be trusted to match. Because of that, the settings-update path only
   * ever compares/writes the config-owned keys for every template, regardless of this flag; a
   * template that returns {@code true} here instead has its schema-manager-driven schema
   * initialization force a full, unconditional rewrite whenever the stored schema version differs
   * from the running one, so a real change to its JSON-owned settings still gets applied on
   * upgrade.
   */
  default boolean hasCustomSettings() {
    return false;
  }
}
