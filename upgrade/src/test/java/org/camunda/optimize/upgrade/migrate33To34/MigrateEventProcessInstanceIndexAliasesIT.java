/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class MigrateEventProcessInstanceIndexAliasesIT extends AbstractMigrateInstanceIndicesIT {

  @SneakyThrows
  @Test
  public void eventProcessInstancesAreMigratedToDedicatedIndexAliases() {
    // given
    final String definitionKey1 = "firstKey";
    final String definitionKey2 = "secondKey";
    executeBulk("steps/3.3/process/33-event-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then the new read aliases point to the correct indices after upgrade
    assertThat(
      getAllDocumentsOfIndexAs(
        PROCESS_INSTANCE_INDEX_PREFIX + definitionKey1,
        ProcessInstanceDto.class
      ))
      .singleElement()
      .extracting(ProcessInstanceDto::getProcessDefinitionKey)
      .isEqualTo(definitionKey1);
    assertThat(getAllDocumentsOfIndexAs(
      PROCESS_INSTANCE_INDEX_PREFIX + definitionKey2,
      ProcessInstanceDto.class
    ))
      .singleElement()
      .extracting(ProcessInstanceDto::getProcessDefinitionKey)
      .isEqualTo(definitionKey2);

    // and the multi alias points to all new instance indices
    assertThat(getAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS)).hasSize(2);
  }
}
