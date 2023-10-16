/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboarding;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.ProcessOverviewService;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.db.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.CustomerOnboardingDataImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Component
@Data
@Slf4j
public class OnboardingSchedulerService extends AbstractScheduledService implements ConfigurationReloadable {

  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessInstanceReader processInstanceReader;
  private final ConfigurationService configurationService;
  private final OnboardingEmailNotificationService onboardingEmailNotificationService;
  private final ProcessOverviewService processOverviewService;
  private final CustomerOnboardingDataImportService onboardingDataService;
  private CCSaaSOnboardingPanelNotificationService saaSPanelNotificationService;
  @Autowired
  private ApplicationContext applicationContext;
  private int intervalToCheckForOnboardingDataInSeconds;
  private Consumer<String> emailNotificationHandler;
  private Consumer<String> panelNotificationHandler;

  @PostConstruct
  public void init() {
    // the default are empty handlers to be set based on configuration and active profile
    // @formatter:off
    emailNotificationHandler = processDefKey -> {};
    panelNotificationHandler = processDefKey -> {};
    // @formatter:on
    setUpScheduler();
  }

  public void setUpScheduler() {
    if (configurationService.getOnboarding().isScheduleProcessOnboardingChecks()) {
      log.info("Initializing OnboardingScheduler");
      // Check no more often than every 60s, recommended 180 (3min)
      setIntervalToCheckForOnboardingDataInSeconds(
        Math.max(60, configurationService.getOnboarding().getIntervalForCheckingTriggerForOnboardingEmails()));
      setupOnboardingEmailNotifications();
      setupOnboardingPanelNotifications();
      startOnboardingScheduling();
    } else {
      log.info("Will not schedule checks for process onboarding state as this is disabled by configuration");
    }
  }

  public void setupOnboardingEmailNotifications() {
    if (configurationService.getOnboarding().isEnableOnboardingEmails()) {
      this.setEmailNotificationHandler(onboardingEmailNotificationService::sendOnboardingEmailWithErrorHandling);
    } else {
      log.info("Onboarding emails deactivated by configuration");
    }
  }

  public void setupOnboardingPanelNotifications() {
    if (applicationContext.containsBeanDefinition(CCSaaSOnboardingPanelNotificationService.class.getSimpleName())) {
      if (configurationService.getPanelNotificationConfiguration().isEnabled()) {
        this.setPanelNotificationHandler(processDefKey -> applicationContext.getBean(CCSaaSOnboardingPanelNotificationService.class)
          .sendOnboardingPanelNotification(processDefKey));
      } else {
        log.info("Onboarding panel notifications deactivated by configuration");
      }
    }
  }

  public void onboardNewProcesses() {
    Set<String> processesNewlyOnboarded = new HashSet<>();
    for (String processToBeOnboarded : processDefinitionReader.getAllNonOnboardedProcessDefinitionKeys()) {
      resolveAnyPendingOwnerAuthorizations(processToBeOnboarded);
      if (processHasStartedInstance(processToBeOnboarded)) {
        emailNotificationHandler.accept(processToBeOnboarded);
        panelNotificationHandler.accept(processToBeOnboarded);
        processesNewlyOnboarded.add(processToBeOnboarded);
      }
    }
    if (!processesNewlyOnboarded.isEmpty()) {
      processDefinitionWriter.markDefinitionKeysAsOnboarded(processesNewlyOnboarded);
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  public synchronized void startOnboardingScheduling() {
    log.info("Starting onboarding scheduling");
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopOnboardingScheduling() {
    log.info("Stopping onboarding scheduling");
    stopScheduling();
  }

  @Override
  protected void run() {
    log.info("Checking whether new data would trigger onboarding");
    onboardNewProcesses();
    log.info("Onboarding check completed");
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ofSeconds(getIntervalToCheckForOnboardingDataInSeconds()));
  }

  private boolean processHasStartedInstance(final String processToBeEvaluated) {
    return processInstanceReader.processDefinitionHasStartedInstances(processToBeEvaluated);
  }

  private void resolveAnyPendingOwnerAuthorizations(final String processToBeOnboarded) {
    processOverviewService.confirmOrDenyOwnershipData(processToBeOnboarded);
  }

}
