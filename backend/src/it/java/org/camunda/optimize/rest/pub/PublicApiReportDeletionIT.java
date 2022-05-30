/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class PublicApiReportDeletionIT extends AbstractIT {
  private static final String ACCESS_TOKEN = "secret_export_token";

  @Test
  public void deleteProcessReport() {
    // given
    setAccessToken();
    final String reportId = reportClient.createEmptySingleProcessReport();

    // when
    final Response deleteResponse = publicApiClient.deleteReport(reportId, ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(SINGLE_PROCESS_REPORT_INDEX_NAME)).isEqualTo(0);
  }

  @Test
  public void deleteManagementProcessReportNotSupported() {
    // given
    setAccessToken();
    final String reportId = createManagementReport();

    // when
    final Response deleteResponse = publicApiClient.deleteReport(reportId, ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void deleteDecisionReport() {
    // given
    setAccessToken();
    final String reportId = reportClient.createEmptySingleDecisionReport();

    // when
    final Response deleteResponse = publicApiClient.deleteReport(reportId, ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(SINGLE_DECISION_REPORT_INDEX_NAME)).isEqualTo(0);
  }

  @Test
  public void deleteReportNotExisting() {
    // given
    setAccessToken();

    // when
    final Response deleteResponse = publicApiClient.deleteReport("notExisting", ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private void setAccessToken() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

  @SneakyThrows
  private String createManagementReport() {
    // The initial report is created for a specific process
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("procDefKey")
      .setProcessDefinitionVersion("1")
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(
      new SingleProcessReportDefinitionRequestDto(reportData));

    final UpdateRequest update = new UpdateRequest()
      .index(ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME)
      .id(reportId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.data.managementReport = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return reportId;
  }

}
