/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
public class Archiver {
  protected static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(Archiver.class);

  @Autowired protected BeanFactory beanFactory;

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected PartitionHolder partitionHolder;

  @Autowired
  @Qualifier("archiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired protected ArchiverRepository archiverRepository;

  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      LOGGER.info("INIT: Start archiving data...");

      // split the list of partitionIds to parallelize
      final List<Integer> partitionIds = partitionHolder.getPartitionIds();
      LOGGER.info("Starting archiver for partitions: {}", partitionIds);
      final int threadsCount = operateProperties.getArchiver().getThreadsCount();
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
          final var archiverJob =
              beanFactory.getBean(ProcessInstancesArchiverJob.class, this, partitionIdsSubset);
          archiverExecutor.execute(archiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          final var batchOperationArchiverJob =
              beanFactory.getBean(BatchOperationArchiverJob.class, this);
          archiverExecutor.execute(batchOperationArchiverJob);
        }
      }
    }
  }

  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final String finishDate,
      final List<Object> ids) {
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);
    return archiverRepository
        .reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenCompose(
            (ignore) -> {
              archiverRepository.setIndexLifeCycle(destinationIndexName);
              return archiverRepository.deleteDocuments(sourceIndexName, idFieldName, ids);
            });
  }

  public String getDestinationIndexName(final String sourceIndexName, final String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }
}
