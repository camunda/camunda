/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.historycleanup;

import static io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures.createAndSaveProcessInstance;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class HistoryCleanupIT {

  @TestTemplate
  public void shouldSaveAndFindByKey(final CamundaRdbmsTestApplication testApplication) {
    final var writers = testApplication.getRdbmsService().createWriter(0);
    final var historyCleanupService =
        new HistoryCleanupService(
            RdbmsWriterConfig.builder().build(),
            writers,
            testApplication.getRdbmsService().getProcessInstanceReader());

    createAndSaveProcessInstance(
        writers,
        ProcessInstanceFixtures.createRandomized(
            b -> b.partitionId(0).historyCleanupDate(OffsetDateTime.now().minusSeconds(1))));

    historyCleanupService.cleanupHistory(0, OffsetDateTime.now());

    // this test should only check that no exceptions are thrown during the cleanup and the SQLs are
    // syntactically correct
  }
}
