/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ArchiverITRepository {
  List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids) throws IOException;

  List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids) throws IOException;

  Optional<List<Long>> getIds(String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex) throws IOException;
}
