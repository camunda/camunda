/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historydeletion;

import static io.camunda.zeebe.protocol.record.value.HistoryDeletionType.PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import io.camunda.db.rdbms.write.service.HistoryDeletionWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryDeletionIT {

  private static final int PARTITION_ID = 0;
  private static final Long RESOURCE_KEY = 2251799813685312L;
  private static final Long BATCH_OPERATION_KEY = 2251799813685385L;

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriter rdbmsWriter;
  private HistoryDeletionDbReader historyDeletionReader;
  private HistoryDeletionWriter historyDeletionWriter;

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    historyDeletionReader = rdbmsService.getHistoryDeletionDbReader();
    historyDeletionWriter = rdbmsWriter.getHistoryDeletionWriter();
  }

  @AfterEach
  void tearDown() {
    historyDeletionWriter.delete(RESOURCE_KEY, BATCH_OPERATION_KEY);
    rdbmsWriter.flush();
  }

  private void writeHistoryDeletion(final HistoryDeletionWriter historyDeletionWriter) {
    historyDeletionWriter.create(
        new HistoryDeletionDbModel.Builder()
            .resourceKey(RESOURCE_KEY)
            .resourceType(PROCESS_INSTANCE)
            .batchOperationKey(BATCH_OPERATION_KEY)
            .partitionId(PARTITION_ID)
            .build());
  }

  @TestTemplate
  public void shouldInsertHistoryDeletion() {
    // given
    writeHistoryDeletion(historyDeletionWriter);
    rdbmsWriter.flush();

    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 1);

    // then
    assertThat(actual)
        .containsExactly(
            new HistoryDeletionDbModel(
                RESOURCE_KEY, PROCESS_INSTANCE, BATCH_OPERATION_KEY, PARTITION_ID));
  }

  @TestTemplate
  public void shouldBeEmpty() {
    // when
    final var actual = historyDeletionReader.getNextBatch(PARTITION_ID, 1);

    // then
    assertThat(actual).isEmpty();
  }
}
