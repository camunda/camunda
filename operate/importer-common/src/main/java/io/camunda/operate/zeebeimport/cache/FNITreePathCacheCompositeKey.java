/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

/**
 * The composite key for the tree path cache {@link FlowNodeInstanceTreePathCache} that contains the
 * most important properties of a flow node instance to be used by the cache.
 *
 * @param partitionId the partition id where the flow node was processed
 * @param recordKey the key of the flow node
 * @param flowScopeKey the scope key of the flow node, might be equal to the process instance key
 *     (on root)
 * @param processInstanceKey the corresponding process instance key for the flow node
 */
public record FNITreePathCacheCompositeKey(
    int partitionId, long recordKey, long flowScopeKey, long processInstanceKey) {}
