/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade35To36PlanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class MigrateDefinitionDataToFlowNodesIT extends AbstractUpgrade35IT {

  @Test
  public void definitionDataMigrationForInstanceIndices() {
    // given
    executeBulk("steps/3.5/35-process-instances.json");
    executeBulk("steps/3.5/35-event-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade35To36PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<ProcessInstanceDto> instancesAfterUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class
    );

    // and data source has been upgraded
    assertThat(instancesAfterUpgrade)
      .hasSize(9)
      .allSatisfy(instance -> assertThat(instance.getFlowNodeInstances())
        .isNotEmpty()
        .allSatisfy(flowNodeInstanceDto -> {
          assertThat(flowNodeInstanceDto.getDefinitionKey())
            .isNotNull()
            .isEqualTo(instance.getProcessDefinitionKey());
          assertThat(flowNodeInstanceDto.getDefinitionVersion())
            .isNotNull()
            .isEqualTo(instance.getProcessDefinitionVersion());
          assertThat(flowNodeInstanceDto.getTenantId()).isEqualTo(instance.getTenantId());
        }));

    assertThat(instancesAfterUpgrade)
      .filteredOn(processInstanceDto -> processInstanceDto.getProcessDefinitionKey().equals("ReviewInvoice"))
      .hasSize(3)
      .allSatisfy(instance -> {
        assertThat(instance.getFlowNodeInstances())
          .isNotEmpty()
          .allSatisfy(flowNodeInstanceDto -> {
            assertThat(flowNodeInstanceDto.getTenantId()).isEqualTo("tenant1");
          });
      });
  }

}
