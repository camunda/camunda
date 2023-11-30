/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.digest;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessDigestNotificationIT extends AbstractPlatformIT {

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then we receive one email straight away from the update
    assertThat(greenMail.waitForIncomingEmail(10, 1)).isTrue();
    greenMail.reset();
    // and one after 1 second from the scheduler
    assertThat(greenMail.waitForIncomingEmail(2000, 1)).isTrue();
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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then digest is sent
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(GreenMailUtil.getBody(emails[0])).contains(DEF_KEY);

    // when digest is disabled
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(false));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    greenMail.reset();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    createKpiCountReport("KPI Report 1");
    createKpiCountReport("KPI Report 2", "0");
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
      .containsIgnoringWhitespaces("50%</span> of your quality KPIs met their targets")
      .containsIgnoringWhitespaces("Quality")
      .containsIgnoringWhitespaces("KPI Report 1")
      .containsIgnoringWhitespaces("KPI Report 2")
      .containsIgnoringWhitespaces("< 1") // target
      .containsIgnoringWhitespaces("--") // change
      .containsIgnoringWhitespaces("1.0"); // current
  }

  @Test
  public void correctEmailContent_noSuccessfulKpiReportsExist() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    createKpiCountReport("KPI Report 1", "0");
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
      .containsIgnoringWhitespaces("0%</span> of your quality KPIs met their targets")
      .containsIgnoringWhitespaces("Quality")
      .containsIgnoringWhitespaces("KPI Report 1")
      .containsIgnoringWhitespaces("< 0") // target
      .containsIgnoringWhitespaces("--") // change
      .containsIgnoringWhitespaces("1.0"); // current
  }

  @Test
  public void emailContainsCorrectLinks() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    final String reportId = createKpiCountReport("KPI Report 1");
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
      final String reportId = createKpiCountReport("KPI Report 1");
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
  public void latestDigestKpiResultsAreUpdated() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto());
    final String reportId = createKpiCountReport("KPI Report 2");
    runKpiSchedulerAndRefreshIndices();

    // then
    assertThat(databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();

    // then
    assertThat(databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME,
      ProcessOverviewDto.class
    ))
      .extracting(ProcessOverviewDto::getDigest)
      .extracting(ProcessDigestDto::getKpiReportResults)
      .singleElement()
      .isEqualTo(Map.of(reportId, "1.0"));
  }

  @Test
  public void latestDigestKpiResultsAreUpdatedEvenWithNullValuedReportResults() {
    // given a report with a null value
    engineIntegrationExtension.deployProcessAndGetId(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcess(DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto());
    final String reportId = createKpiDurationReport("KPI Report 2");
    runKpiSchedulerAndRefreshIndices();
    Map<String, String> expectedResultMap = new HashMap<>();
    expectedResultMap.put(reportId, null);

    // when the digest is run
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();

    // then the null valued report is correctly saved in the most recent KPI report results
    assertThat(databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME, ProcessOverviewDto.class))
      .singleElement()
      .satisfies(overview -> {
        assertThat(overview)
          .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
          .isEqualTo(expectedResultMap);
        // and the null valued report is correctly saved in the digest baseline results
        assertThat(overview)
          .extracting(ProcessOverviewDto::getDigest)
          .extracting(ProcessDigestDto::getKpiReportResults)
          .isEqualTo(expectedResultMap);
      });

    // when the digest is run again
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();

    // then the digest and most recent results still reflect the state correctly
    assertThat(databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
      PROCESS_OVERVIEW_INDEX_NAME, ProcessOverviewDto.class))
      .singleElement()
      .satisfies(overview -> {
        assertThat(overview)
          .extracting(ProcessOverviewDto::getLastKpiEvaluationResults)
          .isEqualTo(expectedResultMap);
        // and the null valued report is correctly saved in the digest baseline results
        assertThat(overview)
          .extracting(ProcessOverviewDto::getDigest)
          .extracting(ProcessDigestDto::getKpiReportResults)
          .isEqualTo(expectedResultMap);
      });
  }

  @Test
  public void digestUpdateIsNullSafeForPreviousKpiResults() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    databaseIntegrationTestExtension.addEntryToDatabase(
      PROCESS_OVERVIEW_INDEX_NAME,
      DEF_KEY,
      new ProcessOverviewDto(DEFAULT_USERNAME, DEF_KEY, new ProcessDigestDto(false, null), Collections.emptyMap())
    );
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    processOverviewClient.updateProcess(
      DEF_KEY, DEFAULT_USERNAME, new ProcessDigestRequestDto(true));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then email sending does not fail
    assertThat(greenMail.waitForIncomingEmail(1000, 1)).isTrue();
  }

  private String createKpiCountReport(final String reportName) {
    return createKpiCountReport(reportName, "1");
  }

  private String createKpiCountReport(final String reportName, final String target) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(DEF_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setIsBelow(true);
    reportDataDto.getConfiguration().getTargetValue().getCountProgress().setTarget(target);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(reportName);
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createKpiDurationReport(final String reportName) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .definitions(List.of(new ReportDataDefinitionDto(DEF_KEY)))
      .build();
    reportDataDto.getConfiguration().getTargetValue().setIsKpi(true);
    TargetDto targetDto = new TargetDto();
    targetDto.setValue("999");
    targetDto.setIsBelow(true);
    targetDto.setUnit(TargetValueUnit.HOURS);
    reportDataDto.getConfiguration().getTargetValue().getDurationProgress().setTarget(targetDto);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName(reportName);
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private void runKpiSchedulerAndRefreshIndices() {
    embeddedOptimizeExtension.getKpiSchedulerService().runKpiImportTask();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  private String readEmailHtmlContent(MimeMessage message) {
    Object content = message.getContent();

    if (content instanceof String contentString) {
      return contentString;
    } else if (content instanceof Multipart multipart) {
      return extractHtmlContentFromMultipart(multipart);
    }
    throw new OptimizeIntegrationTestException("Unsupported email content type.");
  }

  private String extractHtmlContentFromMultipart(Multipart multipart) throws IOException, MessagingException {
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      String contentType = part.getContentType();
      if (contentType != null && contentType.toLowerCase().contains("text/html")) {
        return (String) part.getContent();
      } else if (part.getContent() instanceof Multipart) {
        return extractHtmlContentFromMultipart((Multipart) part.getContent());
      }
    }
    throw new OptimizeIntegrationTestException("No HTML content found in the email.");
  }
}
