/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.util.Either;
import java.io.IOException;
import java.util.List;

public interface ImportStore {

  ImportPositionEntity getImportPositionByAliasAndPartitionId(String alias, int partitionId)
      throws IOException;

  Either<Throwable, Boolean> updateImportPositions(
      List<ImportPositionEntity> positions, List<ImportPositionEntity> postImportPositionUpdates);
}
