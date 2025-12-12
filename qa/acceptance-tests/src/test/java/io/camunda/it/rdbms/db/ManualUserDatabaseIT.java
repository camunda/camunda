/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.ProcessInstanceFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test that verifies Camunda works with manually created database users that have
 * restricted privileges. This simulates production-like setups where the database user is not a
 * superuser with all privileges.
 *
 * <p>The test uses init scripts that create a restricted user (camunda_user) with only the
 * necessary privileges to run Camunda operations.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class ManualUserDatabaseIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManualUserDatabaseIT.class);
  private static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldWorkWithManualUser(final CamundaRdbmsTestApplication testApplication) {
    LOGGER.info("Testing with manual user...");

    // Get the RdbmsService
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    assertThat(rdbmsService).isNotNull();

    // Create a writer
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    assertThat(rdbmsWriter).isNotNull();

    // Write and read data to verify manual user has sufficient privileges
    final Long processInstanceKey = nextKey();
    final var processInstance =
        ProcessInstanceFixtures.createRandomized(
            b ->
                b.processInstanceKey(processInstanceKey)
                    .processDefinitionKey(100L)
                    .processDefinitionId("test-process-manual-user")
                    .state(ProcessInstanceState.ACTIVE));

    rdbmsWriter.getProcessInstanceWriter().create(processInstance);
    rdbmsWriter.flush();

    // Verify we can read the data back
    final var reader = rdbmsService.getProcessInstanceReader();
    final var result = reader.findOne(processInstanceKey);

    assertThat(result).isPresent();
    assertThat(result.get().processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.get().processDefinitionId()).isEqualTo("test-process-manual-user");

    LOGGER.info("Successfully validated manual user with restricted privileges");
  }
}
