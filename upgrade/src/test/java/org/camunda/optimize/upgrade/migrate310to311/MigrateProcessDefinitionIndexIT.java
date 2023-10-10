/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

public class MigrateProcessDefinitionIndexIT extends AbstractUpgrade311IT {

  @Test
  public void addTenantIdForProcessDefinitionsOfZeebeEngine_zeebeImportEnabled() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(true);
    executeBulk("steps/3.10/definitions/310-process-definition-index-zeebe-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void addTenantIdForProcessDefinitionsOfZeebeEngine_zeebeImportDisabledButZeebeImportDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/import/310-position-based-import-index-data.json");
    executeBulk("steps/3.10/definitions/310-process-definition-index-zeebe-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void addTenantIdForProcessDefinitionsOfZeebeEngine_zeebeImportDisabledButZeebeInstanceDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/instances/310-process-instance-index-zeebe-data.json");
    executeBulk("steps/3.10/definitions/310-process-definition-index-zeebe-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void tenantIdDoesNotGetUpdatedForProcessDefinitionsOfCambpmEngine() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/definitions/310-process-definition-index-cambpm-data.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndexAs(new ProcessDefinitionIndex().getIndexName(), ProcessDefinitionOptimizeDto.class))
      .hasSize(3)
      .extracting(DefinitionOptimizeResponseDto::getTenantId)
      .containsExactlyInAnyOrder(null, null, "someTenant");
  }

  @Test
  public void tenantIdForEventProcessDefinitionDoesntGetMigrated() {
    // given
    executeBulk("steps/3.10/definitions/310-event-process-definition-cambpm-data.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndexAs(new EventProcessDefinitionIndex().getIndexName(), EventProcessDefinitionDto.class))
      .hasSize(2)
      .extracting(EventProcessDefinitionDto::getTenantId)
      .containsExactlyInAnyOrder(null, "someTenantId");
  }

  private void assertC8TenantIdMigration() {
    assertThat(getAllDocumentsOfIndexAs(new ProcessDefinitionIndex().getIndexName(), ProcessDefinitionOptimizeDto.class))
      .hasSize(2)
      .allSatisfy(processDefinition -> {
        assertThat(processDefinition.getTenantId()).isEqualTo(ZEEBE_DEFAULT_TENANT_ID);
      });
  }

}
