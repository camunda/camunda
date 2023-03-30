/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebe;

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

  /**
   * Retrieves PartitionIds with waiting time of {@value #WAIT_TIME_IN_MS} milliseconds and retries
   * for {@value #MAX_RETRY} times.
   */
  public List<Integer> getPartitionIds() {
    return getPartitionIdsWithWaitingTimeAndRetries(WAIT_TIME_IN_MS, MAX_RETRY);
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
