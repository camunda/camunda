/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface SchemaManager {

  void createSchema();

  /**
   * Creates the ILM/ISM retention policies, the component template and the index templates that
   * make up the schema. Creating an index template also bootstraps its initial backing index, so
   * some indices are created as a side effect; this method does not, however, run the separate step
   * that creates the remaining plain (non-template) indices.
   *
   * <p>Index templates and ILM/ISM policies are not included in Elasticsearch/OpenSearch snapshots.
   * After restoring a backup into an empty cluster the restored indices exist while the templates
   * and policies are missing; without them the archiver would later create indices with an
   * incorrect structure. This method (re)creates any missing templates and policies. It is
   * idempotent: existing templates are left untouched (created with {@code overwrite=false}) and
   * the retention policy is created only when it does not already exist and only when the
   * application is configured to manage it ({@code ilmManagePolicy}), so a restart never overwrites
   * a pre-existing or user-managed one.
   *
   * <p>This is only ever invoked when schema creation is enabled ({@code createSchema=true}), i.e.
   * when the application is permitted to manage the schema and its retention policies. Guarding on
   * that flag is what makes touching ILM/ISM safe here: it avoids altering (or failing to alter,
   * for lack of permissions) a pre-existing retention configuration on restart (see <a
   * href="https://github.com/camunda/camunda/issues/28571">#28571</a>).
   *
   * @see <a href="https://github.com/camunda/camunda/issues/32806">#32806</a>
   */
  void createSchemaTemplatesAndPolicies();

  IndexMapping getExpectedIndexFields(IndexDescriptor indexDescriptor);

  Map<String, IndexMapping> getIndexMappings(String s) throws IOException;

  String getIndexPrefix();

  void updateSchema(Map<IndexDescriptor, Set<IndexMappingProperty>> newFields);

  void createIndex(IndexDescriptor testIndex);

  void updateIndexSettings();
}
