/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebe;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.ApplicationShutdownService;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.webapps.zeebe.PartitionSupplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PartitionHolder {

  public static final long WAIT_TIME_IN_MS = 1000L;

  public static final int MAX_RETRY = 60; // 1 minute

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionHolder.class);

  private List<Integer> partitionIds = new ArrayList<>();

  @Autowired private OperateProperties operateProperties;

  @Autowired
  @Qualifier("operatePartitionSupplier")
  private PartitionSupplier partitionSupplier;

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
          "Operate needs an established connection to Zeebe in order to retrieve Zeebe Partitions. Shutting down...");
      applicationShutdownService.shutdown();
    }
    return ids;
  }

  private List<Integer> getPartitionIdsWithWaitingTimeAndRetries(
      final long waitingTimeInMilliseconds, final int maxRetries) {
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

  /**
   * Modifies the passed collection!
   *
   * @param partitionIds
   */
  protected List<Integer> extractCurrentNodePartitions(List<Integer> partitionIds) {
    final Integer[] configuredIds = operateProperties.getClusterNode().getPartitionIds();
    if (configuredIds != null && configuredIds.length > 0) {
      partitionIds.retainAll(Arrays.asList(configuredIds));
    } else if (operateProperties.getClusterNode().getNodeCount() != null
        && operateProperties.getClusterNode().getCurrentNodeId() != null) {
      final Integer nodeCount = operateProperties.getClusterNode().getNodeCount();
      final Integer nodeId = operateProperties.getClusterNode().getCurrentNodeId();
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
    LOGGER.debug("Requesting partition ids");

    final var result = partitionSupplier.getPartitionsCount();

    if (result.isLeft()) {
      final var t = result.getLeft();
      LOGGER.warn(
          "Error occurred when requesting partition ids from Zeebe client: " + t.getMessage(), t);
    } else {
      final var partitionCount = result.get();
      if (partitionCount > 0) {
        return Optional.of(CollectionUtil.fromTo(1, partitionCount));
      }
    }

    return Optional.empty();
  }

  protected void sleepFor(final long milliseconds) {
    ThreadUtil.sleepFor(milliseconds);
  }

  public void setPartitionSupplier(final PartitionSupplier partitionSupplier) {
    this.partitionSupplier = partitionSupplier;
  }
}
