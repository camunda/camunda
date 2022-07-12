/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboardinglistener;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.ProcessOverviewService;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Component
@Data
@Slf4j
public class OnboardingSchedulerService extends AbstractScheduledService implements ConfigurationReloadable {

  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceReader processInstanceReader;
  private final ConfigurationService configurationService;
  private final OnboardingNotificationService onboardingNotificationService;
  private final ProcessOverviewService processOverviewService;

  private Set<String> onboardedProcessDefinitions = new HashSet<>();
  private int intervalToCheckForOnboardingDataInSeconds;
  private Function<String, Object> notificationHandler;

  @PostConstruct
  public void init() {
    // Check no more often than every 60s, recommended 180 (3min)
    setIntervalToCheckForOnboardingDataInSeconds(
      Math.max(60, configurationService.getOnboarding().getIntervalForCheckingTriggerForOnboardingEmails()));
    log.info("Initializing OnboardingScheduler");
    onboardedProcessDefinitions = new HashSet<>();
    for (String processToBeEvaluated : getAllProcessDefinitionKeys()) {
      if (processHasCompletedInstance(processToBeEvaluated)) {
        onboardedProcessDefinitions.add(processToBeEvaluated);
      }
    }
    if (configurationService.getOnboarding().isEnableOnboardingEmails()) {
      this.setNotificationHandler(processKey -> {
        onboardingNotificationService.notifyOnboarding(processKey);
        return processKey;
      });
    }
    else {
      log.info("Onboarding E-Mails deactivated by configuration");
    }
    startOnboardingScheduling();
  }

  public void checkIfNewOnboardingDataIsPresent() {
    Set<String> processesNotYetOnboarded = getAllProcessDefinitionKeys();
    processesNotYetOnboarded.removeAll(onboardedProcessDefinitions);
    for (String processToBeOnboarded : processesNotYetOnboarded) {
      resolveAnyPendingOwnerAuthorizations(processToBeOnboarded);
      if (processHasCompletedInstance(processToBeOnboarded)) {
        if (configurationService.getOnboarding().isEnableOnboardingEmails()) {
          triggerOnboardingForProcess(processToBeOnboarded);
        }
        onboardedProcessDefinitions.add(processToBeOnboarded);
      }
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  public synchronized boolean startOnboardingScheduling() {
    log.info("Starting onboarding scheduling");
    return startScheduling();
  }

  @PreDestroy
  public synchronized void stopOnboardingScheduling() {
    log.info("Stopping onboarding scheduling");
    stopScheduling();
  }

  @Override
  protected void run() {
    log.info("Checking whether new data would trigger onboarding");
    checkIfNewOnboardingDataIsPresent();
    log.info("Onboarding check completed");
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(getIntervalToCheckForOnboardingDataInSeconds(), TimeUnit.SECONDS);
  }

  private boolean processHasCompletedInstance(final String processToBeEvaluated) {
    return processInstanceReader.processDefinitionHasCompletedInstances(processToBeEvaluated);
  }

  private void resolveAnyPendingOwnerAuthorizations(final String processToBeOnboarded) {
    processOverviewService.confirmOrDenyOwnershipData(processToBeOnboarded);
  }

  private void triggerOnboardingForProcess(final String processToBeOnboarded) {
    log.info("Triggering onboarding for process " + processToBeOnboarded);
    notificationHandler.apply(processToBeOnboarded);
  }

  private Set<String> getAllProcessDefinitionKeys() {
    return processDefinitionReader.getAllProcessDefinitions()
      .stream()
      .filter(definition -> !definition.isEventBased())
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(toSet());
  }
}
