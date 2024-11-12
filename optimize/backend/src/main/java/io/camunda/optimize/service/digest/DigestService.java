/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.digest;

import static io.camunda.optimize.dto.optimize.query.processoverview.KpiType.QUALITY;
import static io.camunda.optimize.dto.optimize.query.processoverview.KpiType.TIME;

import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import io.camunda.optimize.dto.optimize.query.processoverview.KpiType;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.KpiService;
import io.camunda.optimize.service.db.reader.ProcessOverviewReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import io.camunda.optimize.service.email.EmailService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.util.DurationFormatterUtil;
import io.camunda.optimize.service.util.RootUrlGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class DigestService implements ConfigurationReloadable {

  private static final String DIGEST_EMAIL_TEMPLATE = "digestEmailTemplate.ftl";
  private static final String UTM_SOURCE = "digest";
  private static final String UTM_MEDIUM = "email";
  private static final String DEFAULT_LOCALE = "en";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DigestService.class);
  private final ConfigurationService configurationService;
  private final EmailService emailService;
  private final AbstractIdentityService identityService;
  private final KpiService kpiService;
  private final DefinitionService definitionService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;
  private final ReportReader reportReader;
  private final RootUrlGenerator rootUrlGenerator;
  private final Map<String, ScheduledFuture<?>> scheduledDigestTasks = new HashMap<>();
  private ThreadPoolTaskScheduler digestTaskScheduler;

  public DigestService(
      final ConfigurationService configurationService,
      final EmailService emailService,
      final AbstractIdentityService identityService,
      final KpiService kpiService,
      final DefinitionService definitionService,
      final ProcessOverviewWriter processOverviewWriter,
      final ProcessOverviewReader processOverviewReader,
      final ReportReader reportReader,
      final RootUrlGenerator rootUrlGenerator) {
    this.configurationService = configurationService;
    this.emailService = emailService;
    this.identityService = identityService;
    this.kpiService = kpiService;
    this.definitionService = definitionService;
    this.processOverviewWriter = processOverviewWriter;
    this.processOverviewReader = processOverviewReader;
    this.reportReader = reportReader;
    this.rootUrlGenerator = rootUrlGenerator;
  }

  @PostConstruct
  public void init() {
    initTaskScheduler();
    initExistingDigests();
  }

  @PreDestroy
  public void destroy() {
    if (digestTaskScheduler != null) {
      digestTaskScheduler.destroy();
      digestTaskScheduler = null;
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    destroy();
    init();
  }

  public void handleDigestTask(final String processDefinitionKey) {
    LOG.debug("Checking for active digests on process [{}].", processDefinitionKey);
    final ProcessOverviewDto overviewDto =
        processOverviewReader
            .getProcessOverviewByKey(processDefinitionKey)
            .orElseThrow(
                () -> {
                  unscheduleDigest(processDefinitionKey);
                  return new OptimizeRuntimeException(
                      "Overview for process ["
                          + processDefinitionKey
                          + "] no longer exists. Unscheduling"
                          + " respective digest.");
                });

    if (overviewDto.getDigest().isEnabled()) {
      LOG.info("Creating KPI digest for process [{}].", processDefinitionKey);
      sendDigestAndUpdateLatestKpiResults(overviewDto);
    } else {
      LOG.info("Digest on process [{}] is disabled.", processDefinitionKey);
    }
  }

  public void handleProcessUpdate(
      final String processDefKey, final ProcessUpdateDto processUpdateDto) {
    if (processUpdateDto.getProcessDigest().isEnabled()) {
      rescheduleDigest(processDefKey, processUpdateDto.getProcessDigest());
    } else {
      unscheduleDigest(processDefKey);
    }
  }

  private void rescheduleDigest(
      final String processDefKey, final ProcessDigestRequestDto digestRequestDto) {
    unscheduleDigest(processDefKey);
    scheduleDigest(processDefKey);
    if (digestRequestDto.isEnabled()) {
      handleDigestTask(processDefKey); // if digest is enabled, send out immediate test email
    }
  }

  private void initTaskScheduler() {
    if (digestTaskScheduler == null) {
      digestTaskScheduler = new ThreadPoolTaskScheduler();
      digestTaskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
      digestTaskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      digestTaskScheduler.setThreadNamePrefix("DigestTaskScheduler");
      digestTaskScheduler.initialize();
    }
  }

  private void initExistingDigests() {
    LOG.debug("Scheduling digest tasks for all existing enabled process digests.");
    processOverviewReader
        .getAllActiveProcessDigestsByKey()
        .forEach((processDefinitionKey, digest) -> scheduleDigest(processDefinitionKey));
  }

  private void scheduleDigest(final String processDefinitionKey) {
    scheduledDigestTasks.put(
        processDefinitionKey,
        digestTaskScheduler.schedule(
            createDigestTask(processDefinitionKey),
            new CronTrigger(configurationService.getDigestCronTrigger())));
  }

  private void unscheduleDigest(final String processDefinitionKey) {
    Optional.ofNullable(scheduledDigestTasks.remove(processDefinitionKey))
        .ifPresent(task -> task.cancel(true));
  }

  private void sendDigestAndUpdateLatestKpiResults(final ProcessOverviewDto overviewDto) {
    final List<KpiResultDto> mostRecentKpiReportResults =
        kpiService.extractMostRecentKpiResultsForCurrentKpiReportsForProcess(
            overviewDto, DEFAULT_LOCALE);

    try {
      composeAndSendDigestEmail(overviewDto, mostRecentKpiReportResults);
    } catch (final Exception e) {
      LOG.error("Failed to send digest email", e);
    } finally {
      // The most recent results are then set as the baseline for the digest
      updateBaselineKpiReportResults(
          overviewDto.getProcessDefinitionKey(), mostRecentKpiReportResults);
    }
  }

  private void composeAndSendDigestEmail(
      final ProcessOverviewDto overviewDto, final List<KpiResultDto> currentKpiReportResults) {
    final Optional<UserDto> processOwner = identityService.getUserById(overviewDto.getOwner());
    final String definitionName =
        definitionService
            .getLatestCachedDefinitionOnAnyTenant(
                DefinitionType.PROCESS, overviewDto.getProcessDefinitionKey())
            .map(DefinitionOptimizeResponseDto::getName)
            .orElse(overviewDto.getProcessDefinitionKey());

    emailService.sendTemplatedEmailWithErrorHandling(
        processOwner.map(UserDto::getEmail).orElse(null),
        String.format(
            "[%s - Optimize] Process Digest for Process \"%s\"",
            configurationService.getNotificationEmailCompanyBranding(), definitionName),
        DIGEST_EMAIL_TEMPLATE,
        createInputsForTemplate(
            processOwner.map(UserDto::getName).orElse(overviewDto.getOwner()),
            definitionName,
            currentKpiReportResults,
            overviewDto.getDigest().getKpiReportResults()));
  }

  private Map<String, Object> createInputsForTemplate(
      final String ownerName,
      final String processDefinitionName,
      final List<KpiResultDto> currentKpiReportResults,
      final Map<String, String> previousKpiReportResults) {
    return Map.of(
        "ownerName",
        ownerName,
        "processName",
        processDefinitionName,
        "hasTimeKpis",
        currentKpiReportResults.stream().anyMatch(kpiResult -> TIME.equals(kpiResult.getType())),
        "hasQualityKpis",
        currentKpiReportResults.stream().anyMatch(kpiResult -> QUALITY.equals(kpiResult.getType())),
        "successfulTimeKPIPercent",
        calculateSuccessfulKpiInPercent(TIME, currentKpiReportResults),
        "successfulQualityKPIPercent",
        calculateSuccessfulKpiInPercent(QUALITY, currentKpiReportResults),
        "kpiResults",
        getKpiSummaryDtos(processDefinitionName, currentKpiReportResults, previousKpiReportResults),
        "optimizeHomePageLink",
        getOptimizeHomePageLink());
  }

  private int calculateSuccessfulKpiInPercent(
      final KpiType kpiType, final List<KpiResultDto> kpiResults) {
    final long resultCount =
        kpiResults.stream().filter(kpiResult -> kpiType.equals(kpiResult.getType())).count();
    final long successfulResultCount =
        kpiResults.stream()
            .filter(kpiResult -> kpiType.equals(kpiResult.getType()))
            .filter(KpiResultDto::isTargetMet)
            .count();
    return resultCount == 0 ? 0 : (int) (100.0 * successfulResultCount / resultCount);
  }

  private void updateBaselineKpiReportResults(
      final String processDefinitionKey, final List<KpiResultDto> mostRecentKpiReportResults) {
    // We must use a Map that allows null values, so explicitly create a HashMap here
    final Map<String, String> reportIdsToValues = new HashMap<>();
    mostRecentKpiReportResults.forEach(
        result -> reportIdsToValues.put(result.getReportId(), result.getValue()));
    processOverviewWriter.updateProcessDigestResults(
        processDefinitionKey, new ProcessDigestDto(reportIdsToValues));
  }

  private DigestTask createDigestTask(final String processDefinitionKey) {
    return new DigestTask(this, processDefinitionKey);
  }

  private String getOptimizeHomePageLink() {
    return rootUrlGenerator.getRootUrl() + "/#/";
  }

  private String getReportViewLink(final String reportId, final String collectionId) {
    return rootUrlGenerator.getRootUrl() + getReportViewLinkPath(reportId, collectionId);
  }

  private String getReportViewLinkPath(final String reportId, final String collectionId) {
    return Optional.ofNullable(collectionId)
        .map(
            colId ->
                String.format(
                    "/#/collection/%s/report/%s?utm_medium=%s&utm_source=%s",
                    colId, reportId, UTM_MEDIUM, UTM_SOURCE))
        .orElse(
            String.format(
                "/#/report/%s?utm_medium=%s&utm_source=%s", reportId, UTM_MEDIUM, UTM_SOURCE));
  }

  private List<DigestTemplateKpiSummaryDto> getKpiSummaryDtos(
      final String processDefinitionName,
      final List<KpiResultDto> currentKpiReportResults,
      final Map<String, String> previousKpiReportResults) {
    return currentKpiReportResults.stream()
        .map(
            kpiResult -> {
              final Optional<ReportDefinitionDto> reportDefinition =
                  reportReader.getReport(kpiResult.getReportId());
              if (reportDefinition.isEmpty()) {
                LOG.error(
                    "Report [{}] could not be retrieved for creation of digest email for process [{}] because report no longer exists. "
                        + "This report will be excluded from the digest.",
                    kpiResult.getReportId(),
                    processDefinitionName);
              }
              return Pair.of(reportDefinition, kpiResult);
            })
        .filter(kpiReportResultTuple -> kpiReportResultTuple.key().isPresent())
        .map(
            kpiReportResultTuple ->
                new DigestTemplateKpiSummaryDto(
                    kpiReportResultTuple.key().get().getName(),
                    getReportViewLink(
                        kpiReportResultTuple.value().getReportId(),
                        kpiReportResultTuple.key().get().getCollectionId()),
                    kpiReportResultTuple.value(),
                    Optional.ofNullable(previousKpiReportResults)
                        .orElse(Collections.emptyMap())
                        .get(kpiReportResultTuple.value().getReportId())))
        .toList();
  }

  private static double calculatePercentageChange(
      final KpiResultDto kpiResult, final double previousValueAsDouble) {
    try {
      return 100
          * ((Double.parseDouble(kpiResult.getValue()) - previousValueAsDouble)
              / previousValueAsDouble);
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          "Value could not be parsed to number: " + kpiResult.getValue());
    }
  }

  public static class DigestTemplateKpiSummaryDto {

    private final String reportName;
    private final String reportLink;
    private final String kpiType;
    private final boolean targetMet;
    private final String target;
    private final String current;
    private final Double changeInPercent;
    private final KpiChangeType changeType;

    public DigestTemplateKpiSummaryDto(
        final String reportName,
        final String reportLink,
        final KpiResultDto kpiResultDto,
        @Nullable final String previousValue) {
      this.reportName = reportName;
      this.reportLink = reportLink;
      kpiType = StringUtils.capitalize(kpiResultDto.getType().getId());
      targetMet = kpiResultDto.isTargetMet();
      target =
          getKpiTargetString(
              kpiResultDto.getTarget(),
              kpiResultDto.getUnit(),
              kpiResultDto.getMeasure(),
              kpiResultDto.isBelow());
      current = getKpiValueString(kpiResultDto.getValue(), kpiResultDto.getMeasure());
      changeInPercent = getKpiChangeInPercent(kpiResultDto, previousValue);
      changeType =
          evaluateChangeType(kpiResultDto, getKpiChangeInPercent(kpiResultDto, previousValue));
    }

    /**
     * @return a string to indicate report target depending on viewProperty and isBelow, eg "< 2h"
     *     or "> 50.55 %"
     */
    private String getKpiTargetString(
        final String target,
        final TargetValueUnit unit,
        final ViewProperty kpiMeasure,
        final boolean isBelow) {
      final String targetString;
      if (ViewProperty.DURATION.equals(kpiMeasure)) {
        targetString = target + " " + StringUtils.capitalize(unit.getId());
      } else if (ViewProperty.PERCENTAGE.equals(kpiMeasure)) {
        try {
          targetString = String.format("%.2f %%", Double.parseDouble(target));
        } catch (final NumberFormatException e) {
          throw new OptimizeRuntimeException("Value could not be parsed to number: " + target, e);
        }
      } else {
        targetString = target;
      }
      return String.format("%s %s", isBelow ? "<" : ">", targetString);
    }

    private String getKpiValueString(final String value, final ViewProperty kpiMeasure) {
      if (Optional.ofNullable(value).isEmpty()) {
        return "NA";
      }
      if (ViewProperty.DURATION.equals(kpiMeasure)) {
        try {
          return DurationFormatterUtil.formatMilliSecondsToReadableDurationString(
              (long) Double.parseDouble(value));
        } catch (final NumberFormatException e) {
          throw new OptimizeRuntimeException("Value could not be parsed to number: " + value, e);
        }
      } else if (ViewProperty.PERCENTAGE.equals(kpiMeasure)) {
        try {
          return String.format("%.2f %%", Double.parseDouble(value));
        } catch (final NumberFormatException e) {
          throw new OptimizeRuntimeException("Value could not be parsed to number: " + value, e);
        }
      } else {
        return value;
      }
    }

    private Double getKpiChangeInPercent(
        final KpiResultDto kpiResult, @Nullable final String previousValue) {
      final double previousValueAsDouble;
      try {
        previousValueAsDouble =
            previousValue == null ? Double.NaN : Double.parseDouble(previousValue);
      } catch (final NumberFormatException e) {
        throw new OptimizeRuntimeException(
            "Unable to correctly parse previousValue in kpi result: " + previousValue);
      }
      return previousValue == null || previousValueAsDouble == 0.
          ? 0.
          : calculatePercentageChange(kpiResult, previousValueAsDouble);
    }

    private KpiChangeType evaluateChangeType(
        final KpiResultDto kpiResultDto, final Double changeInPercent) {
      if (changeInPercent == 0.) {
        return KpiChangeType.NEUTRAL;
      }
      if (kpiResultDto.isBelow()) {
        return changeInPercent < 0. ? KpiChangeType.GOOD : KpiChangeType.BAD;
      } else {
        return changeInPercent > 0. ? KpiChangeType.GOOD : KpiChangeType.BAD;
      }
    }

    public String getReportName() {
      return reportName;
    }

    public String getReportLink() {
      return reportLink;
    }

    public String getKpiType() {
      return kpiType;
    }

    public boolean isTargetMet() {
      return targetMet;
    }

    public String getTarget() {
      return target;
    }

    public String getCurrent() {
      return current;
    }

    public Double getChangeInPercent() {
      return changeInPercent;
    }

    public KpiChangeType getChangeType() {
      return changeType;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof DigestTemplateKpiSummaryDto;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "DigestService.DigestTemplateKpiSummaryDto(reportName="
          + getReportName()
          + ", reportLink="
          + getReportLink()
          + ", kpiType="
          + getKpiType()
          + ", targetMet="
          + isTargetMet()
          + ", target="
          + getTarget()
          + ", current="
          + getCurrent()
          + ", changeInPercent="
          + getChangeInPercent()
          + ", changeType="
          + getChangeType()
          + ")";
    }
  }

  public enum KpiChangeType {
    GOOD, // compared to previous report value, new value is closer to KPI target
    NEUTRAL, // no change
    BAD // compared to previous report value, new value is further away from KPI target
  }
}
