/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.schema.SchemaManager;

public interface TestSchemaManager extends SchemaManager {
  void deleteSchema();

  void deleteSchemaQuietly();

  void setCreateSchema(boolean createSchema);

  void setIndexPrefix(String indexPrefix);

  void setDefaultIndexPrefix();
}
