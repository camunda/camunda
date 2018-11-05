package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.VariableWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.ProcessDefinitionCleanupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Component
public class OptimizeCleanupService {
  private final static Logger logger = LoggerFactory.getLogger(OptimizeCleanupService.class);

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final FinishedProcessInstanceWriter processInstanceWriter;
  private final VariableWriter variableWriter;

  private TaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

  @Autowired
  public OptimizeCleanupService(final ConfigurationService configurationService,
                                final ProcessDefinitionReader processDefinitionReader,
                                final FinishedProcessInstanceWriter processInstanceWriter,
                                final VariableWriter variableWriter) {
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceWriter = processInstanceWriter;
    this.variableWriter = variableWriter;
  }

  @PostConstruct
  public void init() {
    logger.info("Initializing OptimizeCleanupService");
    getCleanupConfiguration().validate();
    if (getCleanupConfiguration().getEnabled()) {
      startCleanupScheduling();
    }
  }

  public synchronized void startCleanupScheduling() {
    logger.info("Starting cleanup scheduling");
    if (taskScheduler == null) {
      this.taskScheduler = new ThreadPoolTaskScheduler();
      ((ThreadPoolTaskScheduler) this.taskScheduler).initialize();
    }
    if (scheduledTrigger == null) {
      this.scheduledTrigger = taskScheduler.schedule(this::runCleanup, getCronTrigger());
    }
  }

  public boolean isScheduledToRun() {
    return this.scheduledTrigger != null;
  }

  @PreDestroy
  public synchronized void stopCleanupScheduling() {
    if (scheduledTrigger != null) {
      this.scheduledTrigger.cancel(true);
      this.scheduledTrigger = null;
    }
  }

  public void runCleanup() {
    logger.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = OffsetDateTime.now();
    final Set<String> allOptimizeProcessDefinitionKeys = getAllOptimizeProcessDefinitionKeys();

    enforceSpecificProcessKeyConfigurationsHaveMatchIn(allOptimizeProcessDefinitionKeys);

    allOptimizeProcessDefinitionKeys
      .forEach(currentProcessDefinitionKey -> performCleanupForKey(startTime, currentProcessDefinitionKey));

    final long durationSeconds = OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
    logger.info("Finished optimize history cleanup in {}s", durationSeconds);
  }

  private void performCleanupForKey(OffsetDateTime startTime, String currentProcessDefinitionKey) {
    final ProcessDefinitionCleanupConfiguration cleanupConfigurationForKey = getCleanupConfiguration()
      .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);

    logger.info(
      "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
      currentProcessDefinitionKey,
      cleanupConfigurationForKey.getTtl(),
      cleanupConfigurationForKey.getCleanupMode()
    );
    final OffsetDateTime endDateFilter = startTime.minus(cleanupConfigurationForKey.getTtl());
    switch (cleanupConfigurationForKey.getCleanupMode()) {
      case ALL:
        processInstanceWriter.deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(
          currentProcessDefinitionKey,
          endDateFilter
        );
        break;
      case VARIABLES:
        variableWriter.deleteAllInstanceVariablesByProcessDefinitionKeyAndEndDateOlderThan(
          currentProcessDefinitionKey,
          endDateFilter
        );
        break;
      default:
        throw new IllegalStateException("Unsupported cleanup mode " + cleanupConfigurationForKey.getCleanupMode());
    }
  }

  private void enforceSpecificProcessKeyConfigurationsHaveMatchIn(Set<String> allOptimizeProcessDefinitionKeys) {
    final Set<String> processSpecificConfigurationKeys = getCleanupConfiguration().getAllProcessSpecificConfigurationKeys();
    processSpecificConfigurationKeys.removeAll(allOptimizeProcessDefinitionKeys);
    if (processSpecificConfigurationKeys.size() > 0) {
      final String message =
        "History Cleanup Configuration contains process definition keys for which there is no "
          + "process definition imported yet, aborting this cleanup run to avoid unintended data loss."
          + "The keys without a match in the database are: " + processSpecificConfigurationKeys;
      logger.error(message);
      throw new OptimizeConfigurationException(message);
    }
  }

  private Set<String> getAllOptimizeProcessDefinitionKeys() {
    return processDefinitionReader.getProcessDefinitionsAsService()
      .stream()
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet());
  }

  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private CronTrigger getCronTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }
}
