/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.performance;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {INTEGRATION_TESTS + "=true"})
public class ImportPerformanceZeebeDataTest {

  @RegisterExtension
  @Order(1)
  public DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
      new DatabaseIntegrationTestExtension();

  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension =
      new EmbeddedOptimizeExtension();

  private OffsetDateTime zeebeImportTestStart;
  private Integer expectedDefinitionCount;
  private Integer expectedInstanceCount;
  private Integer importTimeoutInMinutes;

  @BeforeEach
  public void setup() {
    zeebeImportTestStart = OffsetDateTime.now();
    databaseIntegrationTestExtension.disableCleanup();
    embeddedOptimizeExtension
        .getConfigurationService()
        .getCleanupServiceConfiguration()
        .getProcessDataCleanupConfiguration()
        .setEnabled(false);
    expectedDefinitionCount = Integer.getInteger("DATA_PROCESS_DEFINITION_COUNT");
    expectedInstanceCount = Integer.getInteger("DATA_INSTANCE_COUNT");
    importTimeoutInMinutes = Integer.getInteger("IMPORT_TIMEOUT_IN_MINUTES");
  }

  @Test
  public void zeebeDataImportPerformanceTest() {
    // given exported zeebe record indices already imported to ES
    log.info("Starting Zeebe import tests..");

    // when
    importAllZeebeDataOrTimeout();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertImportedData();
  }

  private void assertImportedData() {
    assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME))
        .isEqualTo(expectedDefinitionCount);

    assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS))
        .isEqualTo(expectedInstanceCount);
  }

  private void importAllZeebeDataOrTimeout() {
    final ZeebeImportScheduler zeebeImportScheduler = getZeebeImportScheduler();
    do {
      zeebeImportScheduler.runImportRound();
      if (ChronoUnit.MINUTES.between(zeebeImportTestStart, OffsetDateTime.now())
          >= importTimeoutInMinutes) {
        log.warn("Import timeout of {} minutes reached.", importTimeoutInMinutes);
        break;
      }
    } while (zeebeImportScheduler.isImporting());
  }

  private ZeebeImportScheduler getZeebeImportScheduler() {
    return embeddedOptimizeExtension
        .getImportSchedulerManager()
        .getZeebeImportScheduler()
        .orElseThrow(() -> new OptimizeIntegrationTestException("No ZeebeImportScheduler found."));
  }
}
