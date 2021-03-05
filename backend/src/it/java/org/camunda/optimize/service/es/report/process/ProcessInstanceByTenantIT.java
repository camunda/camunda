/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isOneOf;

public class ProcessInstanceByTenantIT extends AbstractProcessDefinitionIT {

  @Test
  public void reportAcrossTenants_tenantDefinition_singleTenantSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Lists.newArrayList(null, tenantId1, tenantId2), Lists.newArrayList(tenantId1));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_noneTenantSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Lists.newArrayList(null, tenantId1, tenantId2), Lists.newArrayList((String) null));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_multipleTenantsSelected() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Lists.newArrayList(null, tenantId1, tenantId2), Lists.newArrayList(tenantId1, tenantId2));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_multipleTenantsSelectedIncludingNone() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(Lists.newArrayList(null, tenantId1, tenantId2), Lists.newArrayList(null, tenantId1, tenantId2));
  }

  @Test
  public void reportAcrossTenants_tenantDefinition_emptyTenantListDefaultsToEmptyTenantList() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    testTenantFiltering(
      Lists.newArrayList(null, tenantId1, tenantId2),
      Lists.newArrayList()
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
    ReportResultResponseDto<List<RawDataProcessInstanceDto>> result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) expectedTenants.size()));
    result.getData().forEach(rawDataDecisionInstanceDto -> assertThat(
      rawDataDecisionInstanceDto.getTenantId(),
      isOneOf(expectedTenants.toArray())
    ));
  }

}
