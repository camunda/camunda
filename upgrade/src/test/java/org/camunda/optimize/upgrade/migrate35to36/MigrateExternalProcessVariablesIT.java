/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import lombok.SneakyThrows;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade36To37PlanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

public class MigrateExternalProcessVariablesIT extends AbstractUpgrade36IT {

  @SneakyThrows
  @Test
  public void valueInfoFieldIsAddedToExternalVariables() {
    // given
    executeBulk("steps/3.6/externalVariable/36-external-variables.json");
    final UpgradePlan upgradePlan = new Upgrade36To37PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndex(EXTERNAL_PROCESS_VARIABLE_INDEX.getIndexName()))
      .hasSize(2)
      .allSatisfy(externalVariable -> assertThat(externalVariable.getSourceAsMap())
        .containsEntry(SERIALIZATION_DATA_FORMAT, null));
  }
}
