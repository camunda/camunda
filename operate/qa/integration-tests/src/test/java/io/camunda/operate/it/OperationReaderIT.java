/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OperationReaderIT extends OperateSearchAbstractIT {

  private static final String BATCH_OPERATION_ID = "123";
  private static final String ENTITY_KEY_1 = "1";
  private static final String ENTITY_KEY_2 = "2";
  private static final String ENTITY_KEY_3 = "3";
  @Autowired OperationReader operationReader;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private DateTimeFormatter dateTimeFormatter;
  @Autowired private CamundaAuthenticationProvider camundaAuthenticationProvider;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final OperationEntity oe1 =
        new OperationEntity()
            .setBatchOperationId(BATCH_OPERATION_ID)
            .setUsername(DEFAULT_USER)
            .setId(ENTITY_KEY_1);
    final OperationEntity oe2 =
        new OperationEntity()
            .setBatchOperationId(BATCH_OPERATION_ID)
            .setUsername(DEFAULT_USER)
            .setId(ENTITY_KEY_2);
    final OperationEntity oe3 =
        new OperationEntity()
            .setBatchOperationId(BATCH_OPERATION_ID)
            .setUsername("otheruser")
            .setId(ENTITY_KEY_3);

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), ENTITY_KEY_1, oe1);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), ENTITY_KEY_2, oe2);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), ENTITY_KEY_3, oe3);
    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void onlyReturnOperationsStartedByUser() {
    final List<OperationDto> result =
        operationReader.getOperationsByBatchOperationId(BATCH_OPERATION_ID);
    assertThat(result)
        .hasSize(2)
        .extracting(OperationDto::getId)
        .containsExactlyInAnyOrder(ENTITY_KEY_1, ENTITY_KEY_2);
  }
}
