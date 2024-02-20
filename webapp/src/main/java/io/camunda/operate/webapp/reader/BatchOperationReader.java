/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import java.util.List;

public interface BatchOperationReader {
  List<BatchOperationEntity> getBatchOperations(BatchOperationRequestDto batchOperationRequestDto);
}
