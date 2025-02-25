/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.utils.CloneResult;
import io.camunda.exporter.utils.ReindexResult;
import java.util.List;

public interface PrefixMigrationClient {
  ReindexResult reindex(String source, String destination);

  CloneResult clone(String source, String destination);

  List<String> getAllHistoricIndices(String prefix);
}
