/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom32To33;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkAllDefinitionsAsNotDeletedUpgradeIT extends AbstractUpgrade32IT {

  @SneakyThrows
  @Test
  public void processDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definition_data/32-process-definition-bulk.json");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<ProcessDefinitionOptimizeDto> processDefinitions = getAllDocumentsOfIndexAs(
      PROCESS_DEFINITION_INDEX.getIndexName(),
      ProcessDefinitionOptimizeDto.class
    );
    assertThat(processDefinitions)
      .hasSize(2)
      .allSatisfy(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @SneakyThrows
  @Test
  public void decisionDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definition_data/32-decision-definition-bulk.json");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<DecisionDefinitionOptimizeDto> decisionDefinitions = getAllDocumentsOfIndexAs(
      DECISION_DEFINITION_INDEX.getIndexName(),
      DecisionDefinitionOptimizeDto.class
    );
    assertThat(decisionDefinitions)
      .hasSize(2)
      .allSatisfy(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @SneakyThrows
  @Test
  public void eventProcessDefinitionsAreMarkedAsDeleted() {
    // given
    executeBulk("steps/3.2/definition_data/32-event-process-definition-bulk.json");
    final UpgradePlan upgradePlan = new UpgradeFrom32To33().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<EventProcessDefinitionDto> eventProcessDefinitions = getAllDocumentsOfIndexAs(
      EVENT_PROCESS_DEFINITION_INDEX.getIndexName(),
      EventProcessDefinitionDto.class
    );
    assertThat(eventProcessDefinitions)
      .hasSize(2)
      .allSatisfy(definition -> assertThat(definition.isDeleted()).isFalse());
  }

}
