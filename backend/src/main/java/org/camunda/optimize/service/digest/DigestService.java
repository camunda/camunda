/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.digest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.processoverview.KpiResultDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.EmailSendingService;
import org.camunda.optimize.service.KpiService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.ProcessOverviewReader;
import org.camunda.optimize.service.es.writer.ProcessOverviewWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

@RequiredArgsConstructor
@Component
@Slf4j
public class DigestService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final EmailSendingService emailSendingService;
  private final AbstractIdentityService identityService;
  private final TenantService tenantService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final KpiService kpiService;
  private final DefinitionService definitionService;
  private final ProcessOverviewWriter processOverviewWriter;
  private final ProcessOverviewReader processOverviewReader;

  private final Map<String, ScheduledFuture<?>> scheduledDigestTasks = new HashMap<>();
  private ThreadPoolTaskScheduler digestTaskScheduler;

  @PostConstruct
  public void init() {
    initTaskScheduler();
    initExistingDigests();
  }

  @PreDestroy
  public void destroy() {
    if (this.digestTaskScheduler != null) {
      this.digestTaskScheduler.destroy();
      this.digestTaskScheduler = null;
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    destroy();
    init();
  }

  public void handleDigestTask(final String processDefinitionKey) {
    log.debug("Checking for active digests on process [{}].", processDefinitionKey);
    final ProcessOverviewDto overviewDto = processOverviewReader.getProcessOverviewByKey(processDefinitionKey)
      .orElseThrow(() -> {
        unscheduleDigest(processDefinitionKey);
        return new OptimizeRuntimeException("Overview for process [" + processDefinitionKey + "] no longer exists. Unscheduling" +
                                              " respective digest.");
      });

    if (overviewDto.getDigest().isEnabled()) {
      log.info("Creating KPI digest for process [{}].", processDefinitionKey);
      sendDigestAndUpdateLatestKpiResults(overviewDto);
    } else {
      log.info("Digest on process [{}] is disabled.", processDefinitionKey);
    }
  }

  public void handleProcessUpdate(final String processDefKey, final ProcessUpdateDto processUpdateDto) {
    if (processUpdateDto.getProcessDigest().isEnabled()) {
      rescheduleDigest(processDefKey, processUpdateDto.getProcessDigest());
    } else {
      unscheduleDigest(processDefKey);
    }
  }

  private void rescheduleDigest(final String processDefKey,
                                final ProcessDigestRequestDto digestRequestDto) {
    unscheduleDigest(processDefKey);
    scheduleDigest(processDefKey, digestRequestDto.getCheckInterval());
    if (digestRequestDto.isEnabled()) {
      handleDigestTask(processDefKey); // if digest is enabled, send out immediate test email
    }
  }

  private void initTaskScheduler() {
    if (digestTaskScheduler == null) {
      this.digestTaskScheduler = new ThreadPoolTaskScheduler();
      this.digestTaskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
      this.digestTaskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      this.digestTaskScheduler.setThreadNamePrefix("DigestTaskScheduler");
      this.digestTaskScheduler.initialize();
    }
  }

  private void initExistingDigests() {
    log.debug("Scheduling digest tasks for all existing enabled process digests.");
    processOverviewReader.getAllActiveProcessDigestsByKey().forEach((processDefinitionKey, digest) -> scheduleDigest(
      processDefinitionKey,
      digest.getCheckInterval()
    ));
  }

  private void scheduleDigest(final String processDefinitionKey, final AlertInterval interval) {
    scheduledDigestTasks.put(
      processDefinitionKey,
      digestTaskScheduler.schedule(createDigestTask(processDefinitionKey), createDigestTrigger(interval))
    );
  }

  private void unscheduleDigest(final String processDefinitionKey) {
    Optional.ofNullable(scheduledDigestTasks.remove(processDefinitionKey)).ifPresent(task -> task.cancel(true));
  }

  private void sendDigestAndUpdateLatestKpiResults(final ProcessOverviewDto overviewDto) {
    final Map<String, KpiResultDto> currentKpiReportResults =
      kpiService.getKpiResultsForProcessDefinitionByReportId(
        overviewDto.getProcessDefinitionKey(),
        ZoneId.systemDefault()
      );
    final Optional<UserDto> processOwner = identityService.getUserById(overviewDto.getOwner());
    final String definitionName = definitionService.getDefinition(
      DefinitionType.PROCESS,
      overviewDto.getProcessDefinitionKey(),
      List.of(ALL_VERSIONS),
      tenantService.getTenants().stream().map(TenantDto::getId).collect(toList())
    ).map(DefinitionOptimizeResponseDto::getName).orElse(overviewDto.getProcessDefinitionKey());


    emailSendingService.sendEmailWithErrorHandling(
      processOwner.map(UserDto::getEmail).orElse(null),
      composeDigestEmailText(
        processOwner.map(UserDto::getName).orElse(overviewDto.getOwner()),
        definitionName,
        currentKpiReportResults,
        overviewDto.getDigest().getKpiReportResults()
      ),
      String.format(
        "[%s - Optimize] Process Digest for Process \"%s\"",
        configurationService.getNotificationEmailCompanyBranding(),
        definitionName
      )
    );
    updateLastKpiReportResults(overviewDto.getProcessDefinitionKey(), currentKpiReportResults);
  }

  private String composeDigestEmailText(final String ownerName, final String processDefinitionKey,
                                        final Map<String, KpiResultDto> currentKpiReportResults,
                                        final Map<String, String> previousKpiReportResults) {
    return String.format(
      "Hello %s, %n" +
        "Here is your KPI digest for the Process \"%s\":%n" +
        "There are currently %s KPI reports defined for this process.%n%s",
      ownerName,
      processDefinitionKey,
      currentKpiReportResults.keySet().size(),
      composeKpiReportSummaryText(currentKpiReportResults, previousKpiReportResults)
    );
  }

  private String composeKpiReportSummaryText(final Map<String, KpiResultDto> currentKpiReportResults,
                                             final Map<String, String> previousKpiReportResults) {
    return currentKpiReportResults.entrySet().stream()
      .sorted(Comparator.comparing(entry -> entry.getValue().getReportName()))
      .map(entry -> {
        final String previousKpiResult = Optional.ofNullable(previousKpiReportResults.get(entry.getKey()))
          .map(Object::toString)
          .orElse("-");
        return String.format(
          "KPI Report \"%s\": %n" +
            "Target: %s%n" +
            "Current Value: %s%n" +
            "Previous Value: %s%n",
          entry.getValue().getReportName(),
          entry.getValue().getTarget(),
          entry.getValue().getValue(),
          previousKpiResult
        );
      }).collect(joining("\n"));
  }

  private void updateLastKpiReportResults(final String processDefinitionKey,
                                          final Map<String, KpiResultDto> currentKpiReportResults) {
    updateLastProcessDigestKpiResults(
      processDefinitionKey,
      currentKpiReportResults.values()
        .stream()
        .collect(toMap(KpiResultDto::getReportId, KpiResultDto::getValue))
    );
  }

  private void updateLastProcessDigestKpiResults(final String processDefKey,
                                                 final Map<String, String> previousKpiReportResults) {
    processOverviewWriter.updateProcessDigestResults(
      processDefKey,
      new ProcessDigestDto(previousKpiReportResults)
    );
  }

  private DigestTask createDigestTask(final String processDefinitionKey) {
    return new DigestTask(this, processDefinitionKey);
  }

  private Trigger createDigestTrigger(final AlertInterval digestInterval) {
    return new PeriodicTrigger(durationInMs(digestInterval), TimeUnit.MILLISECONDS);
  }

  private long durationInMs(final AlertInterval checkInterval) {
    final ChronoUnit parsedUnit = ChronoUnit.valueOf(checkInterval.getUnit().name().toUpperCase());
    return Duration.between(
      OffsetDateTime.now(),
      OffsetDateTime.now().plus(checkInterval.getValue(), parsedUnit)
    ).toMillis();
  }

}
