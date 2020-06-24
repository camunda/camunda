/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

public class ProcessInstanceMigrationIT extends AbstractUpgrade30IT {


  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.0/process_instance/30-process-instance-bulk");
    executeBulk("steps/3.0/process_instance/30-process-instance-event-bulk");
    executeBulk("steps/3.0/process_instance/30-process-publish-state-bulk");
  }

  @Test
  public void activitiesInProcessInstanceContainProcessInstanceId() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getProcessInstances()).hasSize(1);
    ProcessInstanceDto processInstance = getProcessInstances().get(0);
    assertThat(processInstance.getEvents())
      .hasSize(3)
      .allSatisfy(
        event ->
          assertThat(event.getProcessInstanceId()).isEqualTo(processInstance.getProcessInstanceId())
      );
  }

  @Test
  public void activitiesInEventProcessInstanceContainProcessInstanceId() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getEventProcessInstances()).hasSize(1);
    ProcessInstanceDto processInstance = getProcessInstances().get(0);
    assertThat(processInstance.getEvents())
      .hasSize(3)
      .allSatisfy(
        event ->
          assertThat(event.getProcessInstanceId()).isEqualTo(processInstance.getProcessInstanceId())
      );
  }

  private List<ProcessInstanceDto> getProcessInstances() {
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_INDEX_NAME, ProcessInstanceDto.class);
  }

  private List<ProcessInstanceDto> getEventProcessInstances() {
    return getAllDocumentsOfIndexAs(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*", ProcessInstanceDto.class);
  }

}
