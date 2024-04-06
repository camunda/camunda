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
package io.camunda.tasklist.zeebe;

import io.camunda.tasklist.ApplicationShutdownService;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PartitionHolder {

  public static final long WAIT_TIME_IN_MS = 1000L;

  public static final int MAX_RETRY = 60;

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionHolder.class);

  private List<Integer> partitionIds = new ArrayList<>();

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired(required = false)
  private ApplicationShutdownService applicationShutdownService;

  /**
   * Retrieves PartitionIds with waiting time of {@value #WAIT_TIME_IN_MS} milliseconds and retries
   * for {@value #MAX_RETRY} times.
   */
  public List<Integer> getPartitionIds() {
    final List<Integer> ids = getPartitionIdsWithWaitingTimeAndRetries(WAIT_TIME_IN_MS, MAX_RETRY);
    if (ids.isEmpty() && applicationShutdownService != null) {
      LOGGER.error(
          "Tasklist needs an established connection to Zeebe in order to retrieve Zeebe Partitions. Shutting down...");
      applicationShutdownService.shutdown();
    }
    return ids;
  }

  public List<Integer> getPartitionIdsWithWaitingTimeAndRetries(
      long waitingTimeInMilliseconds, int maxRetries) {
    int retries = 0;
    while (partitionIds.isEmpty() && retries <= maxRetries) {
      if (retries > 0) {
        sleepFor(waitingTimeInMilliseconds);
      }
      retries++;
      final Optional<List<Integer>> zeebePartitionIds = getPartitionIdsFromZeebe();
      if (zeebePartitionIds.isPresent()) {
        partitionIds = extractCurrentNodePartitions(zeebePartitionIds.get());
      } else {
        if (retries <= maxRetries) {
          LOGGER.info("Partition ids can't be fetched from Zeebe. Try next round ({}).", retries);
        } else {
          LOGGER.info(
              "Partition ids can't be fetched from Zeebe. Return empty partition ids list.");
        }
      }
    }
    return partitionIds;
  }

  /** Modifies the passed collection! */
  protected List<Integer> extractCurrentNodePartitions(List<Integer> partitionIds) {
    final Integer[] configuredIds = tasklistProperties.getClusterNode().getPartitionIds();
    if (configuredIds != null && configuredIds.length > 0) {
      partitionIds.retainAll(Arrays.asList(configuredIds));
    } else if (tasklistProperties.getClusterNode().getNodeCount() != null
        && tasklistProperties.getClusterNode().getCurrentNodeId() != null) {
      final Integer nodeCount = tasklistProperties.getClusterNode().getNodeCount();
      final Integer nodeId = tasklistProperties.getClusterNode().getCurrentNodeId();
      if (nodeId >= nodeCount) {
        LOGGER.warn(
            "Misconfiguration: nodeId [{}] must be strictly less than nodeCount [{}]. No partitions will be selected.",
            nodeId,
            nodeCount);
      }
      partitionIds = CollectionUtil.splitAndGetSublist(partitionIds, nodeCount, nodeId);
    }
    return partitionIds;
  }

  protected Optional<List<Integer>> getPartitionIdsFromZeebe() {
    LOGGER.debug("Requesting partition ids from Zeebe client");
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      final int partitionCount = topology.getPartitionsCount();
      if (partitionCount > 0) {
        return Optional.of(CollectionUtil.fromTo(1, partitionCount));
      }
    } catch (Exception t) {
      LOGGER.warn(
          "Error occurred when requesting partition ids from Zeebe client: " + t.getMessage(), t);
    }
    return Optional.empty();
  }

  protected void sleepFor(long milliseconds) {
    ThreadUtil.sleepFor(milliseconds);
  }
}
