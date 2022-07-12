/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

public class ProcessInstanceByTenantIT extends AbstractProcessDefinitionIT {

  @Test
  public void reportAcrossTenants_tenantDefinition_singleTenantSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2), Collections.singletonList(tenantId1));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_noneTenantSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2), Collections.singletonList(null));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_multipleTenantsSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2), Arrays.asList(tenantId1, tenantId2));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_multipleTenantsSelectedIncludingNone() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Arrays.asList(null, tenantId1, tenantId2), Arrays.asList(null, tenantId1, tenantId2));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_emptyTenantListDefaultsToEmptyTenantList() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(
      Arrays.asList(null, tenantId1, tenantId2),
      Collections.emptyList()
    );
  }

  private void testTenantFiltering(final List<String> deployedTenants,
                                   final List<String> selectedTenants) {
    testTenantFiltering(deployedTenants, selectedTenants, selectedTenants);
  }

  private void testTenantFiltering(final List<String> deployedTenants,
                                   final List<String> selectedTenants,
                                   final List<String> expectedTenants) {
    // given
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(deployedTenants);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result =
      reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) expectedTenants.size());
    assertThat(result.getData())
      .extracting(RawDataProcessInstanceDto::getTenantId)
      .allSatisfy(tenantId -> assertThat(tenantId).isIn(expectedTenants));
  }

}
