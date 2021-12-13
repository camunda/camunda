/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade36To37PlanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateProcessInstanceSubEntityTenantsIT extends AbstractUpgrade36IT {

  @SneakyThrows
  @Test
  public void subEntityTenantFieldIsSet() {
    // given
    executeBulk("steps/3.6/processInstance/36-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade36To37PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndexAs(new ProcessInstanceIndex("*").getIndexName(), ProcessInstanceDto.class))
      .hasSize(3)
      .allSatisfy(processInstance -> {
        assertThat(processInstance.getFlowNodeInstances())
          .allSatisfy(flowNode -> assertThat(flowNode.getTenantId()).isEqualTo(processInstance.getTenantId()));
        assertThat(processInstance.getIncidents())
          .allSatisfy(incident -> assertThat(incident.getTenantId()).isEqualTo(processInstance.getTenantId()));
      });
  }

}
