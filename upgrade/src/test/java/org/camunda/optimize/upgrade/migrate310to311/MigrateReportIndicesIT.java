/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

public class MigrateReportIndicesIT extends AbstractUpgrade311IT {

  @Test
  public void addReportDescriptionField() {
    // given
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new SingleProcessReportIndex().getIndexName()));
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new SingleDecisionReportIndex().getIndexName()));
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new CombinedReportIndex().getIndexName()));
  }

  @Test
  public void migrateRawDataCountColumns() {
    // given
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    final Map<String, SingleProcessReportDefinitionRequestDto> reportById = getAllProcessReportsById();
    assertThat(reportById).hasSize(3);
    // @formatter:off
    final SingleProcessReportDefinitionRequestDto reportWithExcludedCountColumns = reportById.get("excludedCountColumnsProcessReport");
    assertThat(reportWithExcludedCountColumns.getData().getConfiguration().getTableColumns().getColumnOrder())
      .containsExactly("processDefinitionKey", "processDefinitionId", "processInstanceId", "startDate",
                       "businessKey", "endDate", "duration", "engineName", "tenantId", "variable:amount", "variable:creditor",
                       "variable:invoiceCategory", "variable:invoiceNumber", "flowNodeDuration:StartEvent_1",
                       "flowNodeDuration:assignReviewer"
      );
    assertThat(reportWithExcludedCountColumns.getData().getConfiguration().getTableColumns().getExcludedColumns())
      .containsExactly("count:incidents", "count:openIncidents", "count:userTasks");

    final SingleProcessReportDefinitionRequestDto reportWithCustomColumnOrder = reportById.get("customOrderColumnsProcessReport");
    assertThat(reportWithCustomColumnOrder.getData().getConfiguration().getTableColumns().getColumnOrder())
      .containsExactly("count:incidents","count:openIncidents","count:userTasks","processDefinitionKey",
                       "processDefinitionId","processInstanceId","businessKey","startDate","endDate","duration","engineName",
                       "tenantId","variable:amount","variable:approved","variable:approver","variable:creditor",
                       "variable:invoiceCategory","variable:invoiceNumber","flowNodeDuration:approveInvoice",
                       "flowNodeDuration:StartEvent_1","flowNodeDuration:assignApprover"
      );
    assertThat(reportWithCustomColumnOrder.getData().getConfiguration().getTableColumns().getExcludedColumns()).isEmpty();

    final SingleProcessReportDefinitionRequestDto reportWithNoTableData = reportById.get("noTableDataProcessReport");
    assertThat(reportWithNoTableData.getData().getConfiguration().getTableColumns().getIncludedColumns()).isEmpty();
    assertThat(reportWithNoTableData.getData().getConfiguration().getTableColumns().getExcludedColumns()).isEmpty();
    assertThat(reportWithNoTableData.getData().getConfiguration().getTableColumns().getColumnOrder()).isEmpty();
    // @formatter:on
  }

  @Test
  public void updateTenantIdForC8ProcessReports_zeebeImportEnabled() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(true);
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void updateTenantIdForC8ProcessReports_zeebeImportDisabledButZeebeImportDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/import/310-position-based-import-index-data.json");
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void updateTenantIdForC8ProcessReports_zeebeImportDisabledButZeebeInstanceDataPresent() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/instances/310-process-instance-index-zeebe-data.json");
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    assertC8TenantIdMigration();
  }

  @Test
  public void doNotUpdateTenantIdForC7ProcessReports() {
    // given
    configurationService.getConfiguredZeebe().setEnabled(false);
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then no changes have been applied to tenantIds of report data sources
    final Map<String, List<String>> reportTenantsById = getAllProcessReportsById().entrySet().stream()
      .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getData().getTenantIds()));
    List<String> nullTenantList = new ArrayList<>();
    nullTenantList.add(null);
    assertThat(reportTenantsById).hasSize(3)
      .containsOnly(
        Map.entry("excludedCountColumnsProcessReport", nullTenantList),
        Map.entry("customOrderColumnsProcessReport", List.of("tenant1")),
        Map.entry("noTableDataProcessReport", List.of("tenant1", "tenant2"))
      );
  }

  private void assertNewDescriptionFieldExists(final SearchHit[] allDocumentsOfIndex) {
    assertThat(allDocumentsOfIndex)
      .allSatisfy(doc -> {
        final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
        assertThat(sourceAsMap)
          .containsEntry("description", null);
      });
  }

  private void assertC8TenantIdMigration() {
    final List<SingleProcessReportDefinitionRequestDto> reports =
      getAllDocumentsOfIndexAs(new SingleProcessReportIndex().getIndexName(), SingleProcessReportDefinitionRequestDto.class);
    assertThat(reports).hasSize(3)
      .extracting(ReportDefinitionDto::getData)
      .flatExtracting(ProcessReportDataDto::getDefinitions)
      .extracting(ReportDataDefinitionDto::getTenantIds)
      .allSatisfy(tenantIds -> assertThat(tenantIds).singleElement().isEqualTo(ZEEBE_DEFAULT_TENANT_ID));
  }

  private Map<String, SingleProcessReportDefinitionRequestDto> getAllProcessReportsById() {
    return getAllDocumentsOfIndexAs(new SingleProcessReportIndex().getIndexName(), SingleProcessReportDefinitionRequestDto.class)
      .stream()
      .collect(toMap(ReportDefinitionDto::getId, Function.identity()));
  }

}
