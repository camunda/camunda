/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.zeebe.PartitionHolder;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class ArchiverStarter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverStarter.class);

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  public ThreadPoolTaskScheduler taskScheduler;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private PartitionHolder partitionHolder;

  @PostConstruct
  public void startArchiving() {
    if (tasklistProperties.getArchiver().isRolloverEnabled()) {
      LOGGER.info("INIT: Start archiving data...");

      // split the list of partitionIds to parallelize
      final List<Integer> partitionIds = partitionHolder.getPartitionIds();
      LOGGER.info("Starting archiver for partitions: {}", partitionIds);
      final int threadsCount = tasklistProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        LOGGER.warn(
            "Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount,
            partitionIds.size());
      }

      for (int i = 0; i < threadsCount; i++) {
        final List<Integer> partitionIdsSubset =
            CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          final TaskArchiverJob batchOperationArchiverJob =
              beanFactory.getBean(TaskArchiverJob.class, partitionIdsSubset);
          taskScheduler.execute(batchOperationArchiverJob);

          final ProcessInstanceArchiverJob processInstanceArchiverJob =
              beanFactory.getBean(ProcessInstanceArchiverJob.class, partitionIdsSubset);
          taskScheduler.execute(processInstanceArchiverJob);
        }
      }
    }
  }
}
