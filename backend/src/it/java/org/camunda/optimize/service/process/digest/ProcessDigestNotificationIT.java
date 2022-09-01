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
import org.apache.commons.mail.util.MimeMessageParser;
import org.camunda.optimize.AbstractIT;
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
    // adjust digest schedule to shorten wait for emails in IT
    embeddedOptimizeExtension.getConfigurationService().setDigestCronTrigger("*/1 * * * * *");
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
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then we receive one email straight away from the update
    assertThat(greenMail.waitForIncomingEmail(10, 1)).isTrue();
    greenMail.reset();
    // and one after 1 second from the scheduler
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
  }

  @Test
  public void dontSendEmailForDisabledDigests() {
    // given one enabled and one disabled digest
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY + "2"));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    processOverviewClient.updateProcess(
      DEF_KEY + "2", DEFAULT_USERNAME, new ProcessDigestRequestDto(false));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then wait a bit to ensure no emails for process 2 are being sent
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
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then digest is sent
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(GreenMailUtil.getBody(emails[0])).contains(DEF_KEY);
    greenMail.reset();

    // when digest is disabled
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(false));
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
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
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
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then email content for process without kpi reports is correct
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    assertThat(greenMail.getReceivedMessages()[0].getSubject())
      .isEqualTo("[Camunda - Optimize] Process Digest for Process \"aProcessDefKey\"");
  }

  @Test
  public void correctEmailContent_noKpiReportsExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    assertThat(readEmailHtmlContent(greenMail.getReceivedMessages()[0]))
      .containsIgnoringWhitespaces("Hi firstName lastName,")
      .containsIgnoringWhitespaces(
        "Here's your digest for the " + DEF_KEY + " process, showing you the current state of your KPIs compared to their " +
          "targets.")
      .containsIgnoringWhitespaces("There are currently no time KPIs defined for this process.")
      .containsIgnoringWhitespaces("There are currently no quality KPIs defined for this process.");
  }

  @Test
  public void correctEmailContent_kpiReportsExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    createKpiReport("KPI Report 1");
    createKpiReport("KPI Report 2");
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));

    // then
    assertThat(greenMail.waitForIncomingEmail(100, 1)).isTrue();
    assertThat(readEmailHtmlContent(greenMail.getReceivedMessages()[0]))
      .containsIgnoringWhitespaces("Hi firstName lastName,")
      .containsIgnoringWhitespaces(
        "Here's your digest for the " + DEF_KEY + " process, showing you the current state of your KPIs compared to their " +
          "targets.")
      .containsIgnoringWhitespaces("There are currently no time KPIs defined for this process.")
      .containsIgnoringWhitespaces("100%</span> of your quality KPIs met their targets")
      .containsIgnoringWhitespaces("Quality")
      .containsIgnoringWhitespaces("KPI Report 1")
      .containsIgnoringWhitespaces("KPI Report 2")
      .containsIgnoringWhitespaces("< 1") // target
      .containsIgnoringWhitespaces("--") // change
      .containsIgnoringWhitespaces("1.0"); // current
  }

  @Test
  public void emailContainsCorrectLinks() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final String reportId = createKpiReport("KPI Report 1");
    importAllEngineEntitiesFromScratch();
    runKpiSchedulerAndRefreshIndices();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));

    // then email contains report and process page links
    assertThat(greenMail.waitForIncomingEmail(100, 1)).isTrue();
    assertThat(readEmailHtmlContent(greenMail.getReceivedMessages()[0]))
      .containsIgnoringWhitespaces(
        "#/report/" + reportId + "?utm_medium=email&utm_source=digest")
      .containsIgnoringWhitespaces("#/processes");
  }

  @Test
  public void emailContainsCorrectLinksUsingCustomContextPath() {
    // given
    try {
      final String customContextPath = "/customContextPath";
      embeddedOptimizeExtension.getConfigurationService().setContextPath(customContextPath);
      engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
      final String reportId = createKpiReport("KPI Report 1");
      importAllEngineEntitiesFromScratch();
      runKpiSchedulerAndRefreshIndices();
      processOverviewClient.updateProcess(
        DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));

      // then email contains report and process page links
      assertThat(greenMail.waitForIncomingEmail(100, 1)).isTrue();
      assertThat(readEmailHtmlContent(greenMail.getReceivedMessages()[0]))
        .containsIgnoringWhitespaces(
          customContextPath + "/#/report/" + reportId + "?utm_medium=email&utm_source=digest")
        .containsIgnoringWhitespaces(customContextPath + "/#/processes");
    } finally {
      embeddedOptimizeExtension.getConfigurationService().setContextPath(null);
    }
  }

  @Test
  public void latestDigestKpiResultsAreUpdated() throws InterruptedException {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto());
    final String reportId = createKpiReport("KPI Report 2");
    runKpiSchedulerAndRefreshIndices();

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
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();

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

  @Test
  public void digestUpdateIsNullSafeForPreviousKpiResults() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_OVERVIEW_INDEX_NAME,
      DEF_KEY,
      new ProcessOverviewDto(DEFAULT_USERNAME, DEF_KEY, new ProcessDigestDto(false, null), Collections.emptyMap())
    );
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then email sending does not fail
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
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

  private void runKpiSchedulerAndRefreshIndices() {
    embeddedOptimizeExtension.getKpiSchedulerService().runKpiImportTask();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  private String readEmailHtmlContent(final MimeMessage message) {
    return new MimeMessageParser(message).parse().getHtmlContent();
  }

}
