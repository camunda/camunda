package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
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
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
public class OptimizeCleanupScheduler {
  private final static Logger logger = LoggerFactory.getLogger(OptimizeCleanupScheduler.class);

  private final ConfigurationService configurationService;
  private final List<OptimizeCleanupService> cleanupServices;

  private TaskScheduler taskScheduler;
  private ScheduledFuture<?> scheduledTrigger;

  @Autowired
  public OptimizeCleanupScheduler(final ConfigurationService configurationService,
                                  final List<OptimizeCleanupService> cleanupServices) {
    this.configurationService = configurationService;
    this.cleanupServices = cleanupServices;
  }

  @PostConstruct
  public void init() {
    logger.info("Initializing OptimizeCleanupScheduler");
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
    logger.info("Stopping cleanup scheduling");
    if (scheduledTrigger != null) {
      this.scheduledTrigger.cancel(true);
      this.scheduledTrigger = null;
    }
  }

  public void runCleanup() {
    logger.info("Running optimize history cleanup...");
    final OffsetDateTime startTime = OffsetDateTime.now();

    cleanupServices.forEach(optimizeCleanupService -> {
      try {
        optimizeCleanupService.doCleanup(startTime);
      } catch (Exception e) {
        logger.error("Exceution of cleanupService {} failed", optimizeCleanupService.getClass().getSimpleName(), e);
      }
    });

    final long durationSeconds = OffsetDateTime.now().minusSeconds(startTime.toEpochSecond()).toEpochSecond();
    logger.info("Finished optimize history cleanup in {}s", durationSeconds);
  }

  public List<OptimizeCleanupService> getCleanupServices() {
    return cleanupServices;
  }

  protected OptimizeCleanupConfiguration getCleanupConfiguration() {
    return this.configurationService.getCleanupServiceConfiguration();
  }

  private CronTrigger getCronTrigger() {
    return new CronTrigger(getCleanupConfiguration().getCronTrigger());
  }
}
