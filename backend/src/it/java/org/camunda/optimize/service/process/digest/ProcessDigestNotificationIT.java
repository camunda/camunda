/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.digest;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.mail.Address;
import javax.mail.internet.MimeMessage;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit.MINUTES;
import static org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit.SECONDS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessDigestNotificationIT extends AbstractIT {

  private static final String DEF_KEY = "aProcessDefKey";

  private static GreenMail greenMail;

  @BeforeEach
  public void beforeEach() {
    embeddedOptimizeExtension.getConfigurationService().setEmailEnabled(true);
    embeddedOptimizeExtension.getConfigurationService().setNotificationEmailAddress("from@localhost.com");
    embeddedOptimizeExtension.getConfigurationService().setNotificationEmailHostname("127.0.0.1");
    embeddedOptimizeExtension.getConfigurationService().setNotificationEmailPort(IntegrationTestConfigurationUtil.getSmtpPort());
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      embeddedOptimizeExtension.getConfigurationService().getEmailAuthenticationConfiguration();
    emailAuthenticationConfiguration.setEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();
    greenMail = new GreenMail(
      new ServerSetup(IntegrationTestConfigurationUtil.getSmtpPort(), null, ServerSetup.PROTOCOL_SMTP)
    );
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
  }

  @AfterEach
  public void cleanUp() {
    greenMail.stop();
  }

  @Test
  public void emailIsSentForEnabledDigest() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then we receive one email straight away and a second email after the interval of 1 second has passed
    assertThat(greenMail.waitForIncomingEmail(1000, 2)).isTrue();
  }

  @Test
  public void updateDigestFrequency() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then receiving one email upon update and one after 1 second
    assertThat(greenMail.waitForIncomingEmail(1000, 2)).isTrue();

    // when
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(5, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then receiving one email upon update and one after 5 seconds
    assertThat(greenMail.waitForIncomingEmail(5000, 2)).isTrue();
  }

  @Test
  public void dontSendEmailForDisabledDigests() {
    // given one enabled and one disabled digest
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY + "2"));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    processOverviewClient.updateProcess(
      DEF_KEY + "2", DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), false));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then wait for however many emails arrive within 1 second (could be 2 or 3 depending on run) and assert none of them are
    // for process 2
    assertThat(greenMail.waitForIncomingEmail(1000, 4)).isFalse();
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).noneMatch(email -> GreenMailUtil.getBody(email).contains(DEF_KEY + "2"));
  }

  @Test
  public void digestsThatGetDisabledStopBeingSent() {
    // given one enabled digest
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then digest is sent
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(GreenMailUtil.getBody(emails[0])).contains(DEF_KEY);
    greenMail.reset();

    // when digest is disabled
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), false));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then no more emails are sent
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isFalse();
  }

  @Test
  @SneakyThrows
  public void correctEmailRecipient() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    assertThat(greenMail.getReceivedMessages()[0].getAllRecipients()).extracting(Address::toString)
      .singleElement()
      .isEqualTo("demo@camunda.org");
  }

  @Test
  @SneakyThrows
  public void correctEmailSubject() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then email content for process without kpi reports is correct
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails[0].getSubject()).isEqualTo("[Camunda - Optimize] Process Digest for Process \"aProcessDefKey\"");
  }

  @Test
  public void correctEmailContent_noKpiReportsExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(GreenMailUtil.getBody(emails[0]))
      .isEqualTo("Hello firstName lastName, \r\n" +
                   "Here is your KPI digest for the Process \"aProcessDefKey\":\r\n" +
                   "There are currently 0 KPI reports defined for this process.");
  }

  @Test
  public void correctEmailContent_kpiReportsExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    createKpiReport("KPI Report 1");
    createKpiReport("KPI Report 2");
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(4, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(greenMail.waitForIncomingEmail(8000, 3)).isTrue();
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(GreenMailUtil.getBody(emails[1]))
      .isEqualTo("Hello firstName lastName, \r\n" +
                   "Here is your KPI digest for the Process \"aProcessDefKey\":\r\n" +
                   "There are currently 2 KPI reports defined for this process.\r\n" +
                   "KPI Report \"KPI Report 1\": \r\n" +
                   "Target: 1\r\n" +
                   "Current Value: 1.0\r\n" +
                   "Previous Value: -\r\n" +
                   "\r\n" +
                   "KPI Report \"KPI Report 2\": \r\n" +
                   "Target: 1\r\n" +
                   "Current Value: 1.0\r\n" +
                   "Previous Value: -");
    assertThat(GreenMailUtil.getBody(emails[2]))
      .isEqualTo("Hello firstName lastName, \r\n" +
                   "Here is your KPI digest for the Process \"aProcessDefKey\":\r\n" +
                   "There are currently 2 KPI reports defined for this process.\r\n" +
                   "KPI Report \"KPI Report 1\": \r\n" +
                   "Target: 1\r\n" +
                   "Current Value: 1.0\r\n" +
                   "Previous Value: 1.0\r\n" +
                   "\r\n" +
                   "KPI Report \"KPI Report 2\": \r\n" +
                   "Target: 1\r\n" +
                   "Current Value: 1.0\r\n" +
                   "Previous Value: 1.0");
  }

  @Test
  public void latestDigestKpiResultsAreUpdated() throws InterruptedException {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto());
    final String reportId = createKpiReport("KPI Report 2");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME,
      ProcessOverviewDto.class
    ))
      .extracting(ProcessOverviewDto::getDigest)
      .extracting(ProcessDigestDto::getKpiReportResults)
      .singleElement()
      .isEqualTo(Collections.emptyMap());

    // given
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(new AlertInterval(1, SECONDS), true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    assertThat(greenMail.waitForIncomingEmail(2000, 2)).isTrue();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME,
      ProcessOverviewDto.class
    ))
      .extracting(ProcessOverviewDto::getDigest)
      .extracting(ProcessDigestDto::getKpiReportResults)
      .singleElement()
      .isEqualTo(Map.of(reportId, "1.0"));
  }

  private String createKpiReport(final String reportName) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(DEF_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget("1");
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(reportName);
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

}
