/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
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

}
