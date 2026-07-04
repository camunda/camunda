/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;

/**
 * The backend-neutral <b>schema</b> seam of the serving store (mirroring OC's {@code
 * schema-manager}): declaring a dataset provisions its physical serving structure — a table
 * (RDBMS), or an index + mapping (Elasticsearch/OpenSearch). Derived entirely from the {@link
 * CompiledDataset}/{@link CompiledTable}, so a new dataset is a declaration, not hand-written DDL.
 *
 * <p>This iteration is {@code MANAGED}-only: the application creates the structure.
 * User-provisioned schema (validate-against-existing + DDL/mapping export) is a later iteration.
 */
public interface DatasetSchemaManager {

  /** Creates the cube's serving structure if absent (idempotent). */
  void ensure(CompiledDataset dataset);

  /** Creates the projected (raw) dataset's serving structure if absent (idempotent). */
  void ensureTable(CompiledTable projection);
}
