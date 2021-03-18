/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.upgrade.plan.indices.ProcessInstanceIndexV5Old;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class MigrateProcessInstancesToDedicatedIndicesIT extends AbstractMigrateInstanceIndicesIT {

  @SneakyThrows
  @Test
  public void processInstancesAreMigratedToDedicatedIndices() {
    // given
    executeBulk("steps/3.3/process/33-process-instances.json");
    final String definitionKey1 = "AnalysisTestingProcess";
    final String definitionKey2 = "leadQualification";
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    // the old instance index has been deleted
    assertThat(indexExists(new ProcessInstanceIndexV5Old())).isFalse();

    // the new instance indices exist for all definitions with instances
    assertThat(indexExists(new ProcessInstanceIndex(definitionKey1))).isTrue();
    assertThat(indexExists(new ProcessInstanceIndex(definitionKey2))).isTrue();

    // all instances have been moved to the correct indices (and they can be reached with the correct alias)
    assertThat(getAllDocumentsOfIndexAs(getProcessInstanceIndexAliasName(definitionKey1), ProcessInstanceDto.class))
      .hasSize(2)
      .extracting(ProcessInstanceDto::getProcessDefinitionKey)
      .containsOnly(definitionKey1);
    assertThat(getAllDocumentsOfIndexAs(getProcessInstanceIndexAliasName(definitionKey2), ProcessInstanceDto.class))
      .singleElement()
      .extracting(ProcessInstanceDto::getProcessDefinitionKey)
      .isEqualTo(definitionKey2);

    // the multi alias points to all new instance indices
    assertThat(getAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS)).hasSize(3);
  }


}
