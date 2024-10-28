/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.onboarding;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.ProcessOverviewService;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessInstanceReader;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.importing.CustomerOnboardingDataImportService;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Component
public class OnboardingSchedulerService extends AbstractScheduledService
    implements ConfigurationReloadable {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OnboardingSchedulerService.class);
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final ProcessInstanceReader processInstanceReader;
  private final ConfigurationService configurationService;
  private final OnboardingEmailNotificationService onboardingEmailNotificationService;
  private final ProcessOverviewService processOverviewService;
  private final CustomerOnboardingDataImportService onboardingDataService;
  private CCSaaSOnboardingPanelNotificationService saaSPanelNotificationService;
  @Autowired private ApplicationContext applicationContext;
  private int intervalToCheckForOnboardingDataInSeconds;
  private Consumer<String> emailNotificationHandler;
  private Consumer<String> panelNotificationHandler;

  public OnboardingSchedulerService(
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessDefinitionWriter processDefinitionWriter,
      final ProcessInstanceReader processInstanceReader,
      final ConfigurationService configurationService,
      final OnboardingEmailNotificationService onboardingEmailNotificationService,
      final ProcessOverviewService processOverviewService,
      final CustomerOnboardingDataImportService onboardingDataService) {
    this.processDefinitionReader = processDefinitionReader;
    this.processDefinitionWriter = processDefinitionWriter;
    this.processInstanceReader = processInstanceReader;
    this.configurationService = configurationService;
    this.onboardingEmailNotificationService = onboardingEmailNotificationService;
    this.processOverviewService = processOverviewService;
    this.onboardingDataService = onboardingDataService;
  }

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
      LOG.info("Initializing OnboardingScheduler");
      // Check no more often than every 60s, recommended 180 (3min)
      setIntervalToCheckForOnboardingDataInSeconds(
          Math.max(
              60,
              configurationService
                  .getOnboarding()
                  .getIntervalForCheckingTriggerForOnboardingEmails()));
      setupOnboardingEmailNotifications();
      setupOnboardingPanelNotifications();
      startOnboardingScheduling();
    } else {
      LOG.info(
          "Will not schedule checks for process onboarding state as this is disabled by configuration");
    }
  }

  public void setupOnboardingEmailNotifications() {
    if (configurationService.getOnboarding().isEnableOnboardingEmails()) {
      setEmailNotificationHandler(
          onboardingEmailNotificationService::sendOnboardingEmailWithErrorHandling);
    } else {
      LOG.info("Onboarding emails deactivated by configuration");
    }
  }

  public void setupOnboardingPanelNotifications() {
    if (applicationContext.containsBeanDefinition(
        CCSaaSOnboardingPanelNotificationService.class.getSimpleName())) {
      if (configurationService.getPanelNotificationConfiguration().isEnabled()) {
        setPanelNotificationHandler(
            processDefKey ->
                applicationContext
                    .getBean(CCSaaSOnboardingPanelNotificationService.class)
                    .sendOnboardingPanelNotification(processDefKey));
      } else {
        LOG.info("Onboarding panel notifications deactivated by configuration");
      }
    }
  }

  public void onboardNewProcesses() {
    final Set<String> processesNewlyOnboarded = new HashSet<>();
    for (final String processToBeOnboarded :
        processDefinitionReader.getAllNonOnboardedProcessDefinitionKeys()) {
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
    LOG.info("Starting onboarding scheduling");
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopOnboardingScheduling() {
    LOG.info("Stopping onboarding scheduling");
    stopScheduling();
  }

  @Override
  protected void run() {
    LOG.info("Checking whether new data would trigger onboarding");
    onboardNewProcesses();
    LOG.info("Onboarding check completed");
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

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return processDefinitionReader;
  }

  public ProcessDefinitionWriter getProcessDefinitionWriter() {
    return processDefinitionWriter;
  }

  public ProcessInstanceReader getProcessInstanceReader() {
    return processInstanceReader;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public OnboardingEmailNotificationService getOnboardingEmailNotificationService() {
    return onboardingEmailNotificationService;
  }

  public ProcessOverviewService getProcessOverviewService() {
    return processOverviewService;
  }

  public CustomerOnboardingDataImportService getOnboardingDataService() {
    return onboardingDataService;
  }

  public CCSaaSOnboardingPanelNotificationService getSaaSPanelNotificationService() {
    return saaSPanelNotificationService;
  }

  public void setSaaSPanelNotificationService(
      final CCSaaSOnboardingPanelNotificationService saaSPanelNotificationService) {
    this.saaSPanelNotificationService = saaSPanelNotificationService;
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public void setApplicationContext(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public int getIntervalToCheckForOnboardingDataInSeconds() {
    return intervalToCheckForOnboardingDataInSeconds;
  }

  public void setIntervalToCheckForOnboardingDataInSeconds(
      final int intervalToCheckForOnboardingDataInSeconds) {
    this.intervalToCheckForOnboardingDataInSeconds = intervalToCheckForOnboardingDataInSeconds;
  }

  public Consumer<String> getEmailNotificationHandler() {
    return emailNotificationHandler;
  }

  public void setEmailNotificationHandler(final Consumer<String> emailNotificationHandler) {
    this.emailNotificationHandler = emailNotificationHandler;
  }

  public Consumer<String> getPanelNotificationHandler() {
    return panelNotificationHandler;
  }

  public void setPanelNotificationHandler(final Consumer<String> panelNotificationHandler) {
    this.panelNotificationHandler = panelNotificationHandler;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OnboardingSchedulerService;
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
    return "OnboardingSchedulerService(processDefinitionReader="
        + getProcessDefinitionReader()
        + ", processDefinitionWriter="
        + getProcessDefinitionWriter()
        + ", processInstanceReader="
        + getProcessInstanceReader()
        + ", configurationService="
        + getConfigurationService()
        + ", onboardingEmailNotificationService="
        + getOnboardingEmailNotificationService()
        + ", processOverviewService="
        + getProcessOverviewService()
        + ", onboardingDataService="
        + getOnboardingDataService()
        + ", saaSPanelNotificationService="
        + getSaaSPanelNotificationService()
        + ", applicationContext="
        + getApplicationContext()
        + ", intervalToCheckForOnboardingDataInSeconds="
        + getIntervalToCheckForOnboardingDataInSeconds()
        + ", emailNotificationHandler="
        + getEmailNotificationHandler()
        + ", panelNotificationHandler="
        + getPanelNotificationHandler()
        + ")";
  }
}
