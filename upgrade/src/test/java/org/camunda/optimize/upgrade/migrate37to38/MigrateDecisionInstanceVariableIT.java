/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDecisionInstanceVariableIT extends AbstractUpgrade37IT {

  @Test
  public void deleteUnsupportedInputOutputVariables() {
    // given
    executeBulk("steps/3.7/decisioninstance/37-decisioninstance.json");

    // when
    performUpgrade();

    // then
    final List<DecisionInstanceDto> migratedInstances = getAllDocumentsOfIndexAs(
      new DecisionInstanceIndex("*").getIndexName(), DecisionInstanceDto.class
    );
    assertThat(migratedInstances)
      .hasSize(6)
      .allSatisfy(decisionInstanceDto -> {
        assertThat(decisionInstanceDto.getInputs())
          .allSatisfy(input -> assertThat(ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES).contains(input.getType()));
        assertThat(decisionInstanceDto.getOutputs())
          .allSatisfy(output -> assertThat(ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES).contains(output.getType()));
      });
    // The four invalid inputs have been removed
    assertThat(migratedInstances).flatExtracting(DecisionInstanceDto::getInputs).hasSize(8);
    // The four invalid outputs have been removed
    assertThat(migratedInstances).flatExtracting(DecisionInstanceDto::getOutputs).hasSize(8);
  }

}
