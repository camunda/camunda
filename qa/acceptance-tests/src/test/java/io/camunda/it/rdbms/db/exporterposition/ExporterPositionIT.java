/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.exporterposition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ExporterPositionIT {

  public static final int PARTITION_ID = 0;
  private static final LocalDateTime NOW = LocalDateTime.now();

  @AfterEach
  void tearDown(final CamundaRdbmsTestApplication testApplication) {
    testApplication.getRdbmsService().createWriter(PARTITION_ID).getRdbmsPurger().purgeRdbms();
  }

  @TestTemplate
  public void shouldFindExporterPositionByPartitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ExporterPositionService exporterPositionService =
        rdbmsWriters.getExporterPositionService();

    final var position = new ExporterPositionModel(PARTITION_ID, "Test exporter", 0L, NOW, NOW);

    exporterPositionService.create(position);
    rdbmsWriters.flush();

    final var readPosition = exporterPositionService.findOne(PARTITION_ID);

    assertThat(position.partitionId()).isEqualTo(readPosition.partitionId());
    assertThat(position.exporter()).isEqualTo(readPosition.exporter());
    assertThat(position.lastExportedPosition()).isEqualTo(readPosition.lastExportedPosition());
  }

  @TestTemplate
  public void shouldSelectForUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final ExporterPositionService exporterPositionService =
        rdbmsWriters.getExporterPositionService();

    final var position1 = new ExporterPositionModel(PARTITION_ID, "Test exporter", 0L, NOW, NOW);

    exporterPositionService.create(position1);
    rdbmsWriters.flush();

    final AtomicLong positionCounter = new AtomicLong(0L);
    exporterPositionService.registerLockPositionHook(PARTITION_ID, positionCounter::get);

    final var position2 = new ExporterPositionModel(2, "Test exporter", 1L, NOW, NOW);

    exporterPositionService.create(position2);
    rdbmsWriters.flush();

    // no exception
  }
}
