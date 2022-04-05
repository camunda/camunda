/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;

@Component
public class PartitionHolder {

  public static final long WAIT_TIME_IN_MS = 1000L;

  public static final int MAX_RETRY = 60;   //1 minute

  private static final Logger logger = LoggerFactory.getLogger(PartitionHolder.class);

  private List<Integer> partitionIds = new ArrayList<>();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  /**
   * Retrieves PartitionIds with waiting time of {@value #WAIT_TIME_IN_MS} milliseconds and retries for {@value #MAX_RETRY} times.
   */
  public List<Integer> getPartitionIds() {
    return getPartitionIdsWithWaitingTimeAndRetries(WAIT_TIME_IN_MS, MAX_RETRY);
  }
  
  private List<Integer> getPartitionIdsWithWaitingTimeAndRetries(long waitingTimeInMilliseconds, int maxRetries) {
    int retries = 0;
    while (partitionIds.isEmpty() && retries <= maxRetries) {
      if (retries > 0) {
        sleepFor(waitingTimeInMilliseconds);
      }
      retries++;
      Optional<List<Integer>> zeebePartitionIds = getPartitionIdsFromZeebe();
      if (zeebePartitionIds.isPresent()) {
        partitionIds = extractCurrentNodePartitions(zeebePartitionIds.get());
      } else {
        if (retries <= maxRetries) {
          logger.info("Partition ids can't be fetched from Zeebe. Try next round ({}).", retries);
        } else {
          logger.info("Partition ids can't be fetched from Zeebe. Return empty partition ids list.");
        }
      }
    }
    return partitionIds;
  }

  /**
   * Modifies the passed collection!
   * @param partitionIds
   */
  protected List<Integer> extractCurrentNodePartitions(List<Integer> partitionIds) {
    Integer[] configuredIds = operateProperties.getClusterNode().getPartitionIds();
    if (configuredIds != null && configuredIds.length > 0) {
      partitionIds.retainAll(Arrays.asList(configuredIds));
    } else if (operateProperties.getClusterNode().getNodeCount() != null &&
              operateProperties.getClusterNode().getCurrentNodeId() != null){
      Integer nodeCount = operateProperties.getClusterNode().getNodeCount();
      Integer nodeId = operateProperties.getClusterNode().getCurrentNodeId();
      if (nodeId >= nodeCount) {
        logger.warn("Misconfiguration: nodeId [{}] must be strictly less than nodeCount [{}]. No partitions will be selected.", nodeId, nodeCount);
      }
      partitionIds = CollectionUtil.splitAndGetSublist(partitionIds, nodeCount, nodeId);
    }
    return partitionIds;
  }
  
  protected Optional<List<Integer>> getPartitionIdsFromZeebe(){
    logger.debug("Requesting partition ids from Zeebe client");
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      int partitionCount = topology.getPartitionsCount();
      if(partitionCount>0) {
        return Optional.of(CollectionUtil.fromTo(1, partitionCount));
      }
    } catch (Exception t) {
      logger.warn("Error occurred when requesting partition ids from Zeebe client: " + t.getMessage(), t);
    }
    return Optional.empty();
  }
  
  protected void sleepFor(long milliseconds) {
    ThreadUtil.sleepFor(milliseconds);
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }
}
