package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.NamedThreadFactory;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.reindex.BulkByScrollTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EsBulkByScrollTaskActionProgressReporter {
  private final Logger logger;
  private final ScheduledExecutorService executorService;
  private final Client esClient;
  private final String action;

  public EsBulkByScrollTaskActionProgressReporter(String loggerName, Client esClient, String action) {
    this.logger = LoggerFactory.getLogger(loggerName);
    this.esClient = esClient;
    this.action = action;
    this.executorService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(loggerName + "-progress"));
  }

  public void start() {
    executorService.scheduleAtFixedRate(
      () -> {
        final List<BulkByScrollTask.Status> currentTasksStatus = esClient.admin()
          .cluster().prepareListTasks()
          .setActions(action)
          .setDetailed(true)
          .get()
          .getTasks()
          .stream()
          .filter(taskInfo -> taskInfo.getStatus() instanceof BulkByScrollTask.Status)
          .map(taskInfo -> (BulkByScrollTask.Status) taskInfo.getStatus())
          .collect(Collectors.toList());

        currentTasksStatus
          .forEach(status -> {
            final long sumOfProcessedDocs = status.getDeleted() + status.getCreated() + status.getUpdated();
            int progress = status.getTotal() > 0
              ? Double.valueOf((double) sumOfProcessedDocs / status.getTotal() * 100.0D).intValue()
              : 0;
            logger.info("Current {} BulkByScrollTaskTask progress: {}%, total: {}, done: {}", action, progress, status.getTotal(), sumOfProcessedDocs);
          });
      },
      0,
      30,
      TimeUnit.SECONDS
    );
  }

  public void stop() {
    try {
      executorService.shutdownNow();
    } catch (Exception e) {
      logger.error("Failed stopping progress reporting thread");
    }
  }

}
