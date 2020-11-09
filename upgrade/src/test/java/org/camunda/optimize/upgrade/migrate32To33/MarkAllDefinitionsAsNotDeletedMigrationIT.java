/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom32To33;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkAllDefinitionsAsNotDeletedMigrationIT extends AbstractUpgrade32IT {

  @SneakyThrows
  @Test
  public void processDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definitions/32-process-definition-bulk");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // then the delete field does not exist
    assertThatDocumentsOfIndexHaveDeletionState(PROCESS_DEFINITION_INDEX.getIndexName(), 2, null);

    // when
    upgradePlan.execute();

    // then the field exists and is marked as false
    assertThatDocumentsOfIndexHaveDeletionState(PROCESS_DEFINITION_INDEX.getIndexName(), 2, false);
  }

  @SneakyThrows
  @Test
  public void decisionDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definitions/32-decision-definition-bulk");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // then the delete field does not exist
    assertThatDocumentsOfIndexHaveDeletionState(DECISION_DEFINITION_INDEX.getIndexName(), 2, null);

    // when
    upgradePlan.execute();

    // then the field exists and is marked as false
    assertThatDocumentsOfIndexHaveDeletionState(DECISION_DEFINITION_INDEX.getIndexName(), 2, false);
  }

  @SneakyThrows
  @Test
  public void eventProcessDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definitions/32-event-process-definition-bulk");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // then the delete field does not exist
    assertThatDocumentsOfIndexHaveDeletionState(EVENT_PROCESS_DEFINITION_INDEX.getIndexName(), 2, null);

    // when
    upgradePlan.execute();

    // then the field exists and is marked as false
    assertThatDocumentsOfIndexHaveDeletionState(EVENT_PROCESS_DEFINITION_INDEX.getIndexName(), 2, false);
  }

  private void assertThatDocumentsOfIndexHaveDeletionState(final String indexName,
                                                           final int expectedSize,
                                                           final Boolean expectedDeletionState) {
    assertThat(getAllDocumentsOfIndex(indexName))
      .hasSize(expectedSize)
      .allSatisfy(def -> {
        final Boolean isDeleted = (Boolean) def.getSourceAsMap().get(DefinitionOptimizeResponseDto.Fields.deleted);
        assertThat(isDeleted).isEqualTo(expectedDeletionState);
      });
  }

}
