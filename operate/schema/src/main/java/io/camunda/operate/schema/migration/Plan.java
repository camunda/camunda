/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.schema.SchemaManager;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** A plan consists of executable Steps. The plan can be execute on schema manager */
public interface Plan {

  String PRESERVE_INDEX_SUFFIX_SCRIPT =
      "ctx._index = params.dstIndex+'_' + (ctx._index.substring(ctx._index.indexOf('_') + 1, ctx._index.length()));";

  default List<Step> getSteps() {
    return Collections.emptyList();
  }

  void executeOn(final SchemaManager schemaManager) throws IOException, MigrationException;

  default void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {}
}
