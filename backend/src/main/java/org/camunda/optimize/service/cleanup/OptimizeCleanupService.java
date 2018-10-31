package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.VariableWriter;
import org.camunda.optimize.service.util.configuration.CleanupMode;
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
import java.time.Period;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Component
public class OptimizeCleanupService {
  private final static Logger logger = LoggerFactory.getLogger(OptimizeCleanupService.class);

  private final OptimizeCleanupConfiguration configuration;
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
    this.configuration = configurationService.getCleanupServiceConfiguration();
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceWriter = processInstanceWriter;
    this.variableWriter = variableWriter;
  }

  @PostConstruct
  public void init() {
    logger.info("Initializing OptimizeCleanupService");
    this.configuration.validate();
    if (configuration.getEnabled()) {
      startCleanupScheduling();
    }
  }

  public synchronized void startCleanupScheduling() {
    logger.info("Starting cleanup scheduling");
    if (taskScheduler == null) {
      this.taskScheduler = new ThreadPoolTaskScheduler();
    }
    if (scheduledTrigger == null) {
      this.scheduledTrigger = taskScheduler.schedule(this::runCleanup, getCronTrigger());
    }
  }

  public void runCleanup() {
    logger.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = OffsetDateTime.now();

    processDefinitionReader.getProcessDefinitionsAsService()
      .stream()
      .map(ProcessDefinitionOptimizeDto::getKey)
      .collect(Collectors.toSet())
      .forEach(currentProcessDefinitionKey -> {
        final Optional<ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration = configuration
          .getProcessDefinitionCleanupConfigurationForKey(currentProcessDefinitionKey);
        final Period ttl = processDefinitionSpecificConfiguration.flatMap(ProcessDefinitionCleanupConfiguration::getTtl)
          .orElse(configuration.getDefaultTtl());
        final CleanupMode mode =
          processDefinitionSpecificConfiguration.flatMap(ProcessDefinitionCleanupConfiguration::getCleanupMode)
            .orElse(configuration.getDefaultMode());

        logger.info(
          "Performing cleanup on process instances for processDefinitionKey: {}, with ttl: {} and mode:{}",
          currentProcessDefinitionKey,
          ttl,
          mode
        );
        final OffsetDateTime endDateFilter = startTime.minus(ttl);
        switch (mode) {
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
            throw new IllegalStateException("Unsupported cleanup mode " + mode);
        }
      });

    final long durationSeconds = OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
    logger.info("Finished optimize history cleanup in {}s", durationSeconds);
  }

  @PreDestroy
  public synchronized void stopCleanupScheduling() {
    if (scheduledTrigger != null) {
      this.scheduledTrigger.cancel(true);
      this.scheduledTrigger = null;
    }
  }

  private CronTrigger getCronTrigger() {
    return new CronTrigger(configuration.getCronTrigger());
  }
}
