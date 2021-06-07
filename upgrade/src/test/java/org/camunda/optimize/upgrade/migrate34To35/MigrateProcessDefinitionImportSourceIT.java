/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.EventsDataSourceDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.upgrade.migrate34To35.indices.DecisionDefinitionIndexV4Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.EventProcessDefinitionIndexV3Old;
import org.camunda.optimize.upgrade.migrate34To35.indices.ProcessDefinitionIndexV4Old;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrateProcessDefinitionImportSourceIT extends AbstractUpgrade34IT {

  @SneakyThrows
  @Test
  public void migrateProcessDefinitionDataImportSource() {
    // given
    executeBulk("steps/3.4/definition/34-process-definition.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    //then
    final ProcessDefinitionIndex newIndex = new ProcessDefinitionIndex();
    assertThat(indexExists(new ProcessDefinitionIndexV4Old())).isFalse();
    assertThat(indexExists(newIndex)).isTrue();
    assertThat(getAllDocumentsOfIndexAs(
      newIndex.getIndexName(),
      ProcessDefinitionOptimizeDto.class
    )).extracting(ProcessDefinitionOptimizeDto::getDataSource)
      .hasSize(1003)
      .containsOnly(new EngineDataSourceDto("camunda-bpm"), new EngineDataSourceDto("some-other-engine"))
      .containsOnlyOnce(new EngineDataSourceDto("some-other-engine"));
    assertThat(getAllDocumentsOfIndex(newIndex.getIndexName()))
      .allSatisfy(hit -> assertThat(hit.getSourceAsMap()).doesNotContainKey("engine"));
  }

  @SneakyThrows
  @Test
  public void migrateDecisionDefinitionDataImportSource() {
    // given
    executeBulk("steps/3.4/definition/34-decision-definition.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    //then
    final DecisionDefinitionIndex newIndex = new DecisionDefinitionIndex();
    assertThat(indexExists(new DecisionDefinitionIndexV4Old())).isFalse();
    assertThat(indexExists(newIndex)).isTrue();
    assertThat(getAllDocumentsOfIndexAs(
      newIndex.getIndexName(),
      DecisionDefinitionOptimizeDto.class
    )).extracting(DecisionDefinitionOptimizeDto::getDataSource)
      .hasSize(2)
      .containsExactlyInAnyOrder(
        new EngineDataSourceDto("camunda-bpm"), new EngineDataSourceDto("some-other-engine")
      );
    assertThat(getAllDocumentsOfIndex(newIndex.getIndexName()))
      .allSatisfy(hit -> assertThat(hit.getSourceAsMap()).doesNotContainKey("engine"));
  }

  @SneakyThrows
  @Test
  public void migrateEventProcessDefinitionDataImportSource() {
    // given
    executeBulk("steps/3.4/definition/34-event-process-definition.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    //then
    final EventProcessDefinitionIndex newIndex = new EventProcessDefinitionIndex();
    assertThat(indexExists(new EventProcessDefinitionIndexV3Old())).isFalse();
    assertThat(indexExists(newIndex)).isTrue();
    assertThat(getAllDocumentsOfIndexAs(
      newIndex.getIndexName(),
      EventProcessDefinitionDto.class
    )).extracting(ProcessDefinitionOptimizeDto::getDataSource)
      .hasSize(1003)
      .containsOnly(new EventsDataSourceDto());
    assertThat(getAllDocumentsOfIndex(newIndex.getIndexName()))
      .allSatisfy(hit -> assertThat(hit.getSourceAsMap()).doesNotContainKey("engine"));
  }

}
