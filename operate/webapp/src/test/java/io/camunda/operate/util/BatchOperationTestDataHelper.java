/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.OperationTypeDto;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class BatchOperationTestDataHelper {

  public static final String[] TEST_BATCH_OP_IDS = {
    "00000000-0000-0000-0000-000000000000",
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222"
  };
  public static final String TEST_USER = "testuser";
  private static final OffsetDateTime TEST_DATE_TIME =
      OffsetDateTime.of(2000, 1, 1, 1, 1, 1, 1, ZoneOffset.MIN);

  public static List<BatchOperationEntity> create2TestBatchOperationDtos() {
    final BatchOperationEntity testEntity1 =
        new BatchOperationEntity()
            .setId(TEST_BATCH_OP_IDS[0])
            .setName("entity1")
            .setType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(5)
            .setOperationsFinishedCount(5)
            .setUsername(TEST_USER);
    final BatchOperationEntity testEntity2 =
        new BatchOperationEntity()
            .setId(TEST_BATCH_OP_IDS[1])
            .setName("entity2")
            .setType(OperationType.MODIFY_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(3)
            .setOperationsFinishedCount(3)
            .setUsername(TEST_USER);

    final List<BatchOperationEntity> testEntities = new ArrayList<>(2);
    testEntities.add(testEntity1);
    testEntities.add(testEntity2);
    return testEntities;
  }

  public static List<BatchOperationDto> get2DtoBatchRequestExpected() {
    final BatchOperationDto expectedDto1 =
        new BatchOperationDto()
            .setId(TEST_BATCH_OP_IDS[0])
            .setName("entity1")
            .setType(OperationTypeDto.CANCEL_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(5)
            .setOperationsFinishedCount(5)
            .setFailedOperationsCount(4)
            .setCompletedOperationsCount(1);
    final BatchOperationDto expectedDto2 =
        new BatchOperationDto()
            .setId(TEST_BATCH_OP_IDS[1])
            .setName("entity2")
            .setType(OperationTypeDto.MODIFY_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(3)
            .setOperationsFinishedCount(3)
            .setFailedOperationsCount(0)
            .setCompletedOperationsCount(3);
    return List.of(expectedDto1, expectedDto2);
  }

  public static List<BatchOperationDto> get2DtoBatchRequestEmptyAggregationsExpected() {
    final BatchOperationDto expectedDto1 =
        new BatchOperationDto()
            .setId(TEST_BATCH_OP_IDS[0])
            .setName("entity1")
            .setType(OperationTypeDto.CANCEL_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(5)
            .setOperationsFinishedCount(0)
            .setFailedOperationsCount(0)
            .setCompletedOperationsCount(0);
    final BatchOperationDto expectedDto2 =
        new BatchOperationDto()
            .setId(TEST_BATCH_OP_IDS[0])
            .setName("entity2")
            .setType(OperationTypeDto.MODIFY_PROCESS_INSTANCE)
            .setStartDate(TEST_DATE_TIME)
            .setEndDate(TEST_DATE_TIME)
            .setInstancesCount(3)
            .setOperationsFinishedCount(0)
            .setFailedOperationsCount(0)
            .setCompletedOperationsCount(0);
    return List.of(expectedDto1, expectedDto2);
  }
}
