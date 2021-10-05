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

public class MigrateDefinitionDataToIncidentsIT extends AbstractUpgrade35IT {

  @Test
  public void definitionDataMigrationForInstanceIndices() {
    // given
    executeBulk("steps/3.5/35-process-instances.json");
    final UpgradePlan upgradePlan = new Upgrade35To36PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<ProcessInstanceDto> instancesAfterUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_INSTANCE_MULTI_ALIAS, ProcessInstanceDto.class
    );
    // definition data has been set on the instance with incidents
    assertThat(instancesAfterUpgrade)
      .filteredOn(processInstanceDto -> processInstanceDto.getProcessInstanceId().equals("abf539c7-f034-11eb-bc34-0242ac130004"))
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getIncidents())
          .hasSize(2)
          .allSatisfy(incidentInstance -> {
            assertThat(incidentInstance.getDefinitionKey()).isEqualTo("ReviewInvoice");
            assertThat(incidentInstance.getDefinitionVersion()).isEqualTo("1");
            assertThat(incidentInstance.getTenantId()).isEqualTo("tenant1");
          });
      });
  }

}
