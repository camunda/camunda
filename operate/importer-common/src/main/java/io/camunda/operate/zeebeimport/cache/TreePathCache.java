/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.cache;

public interface TreePathCache {

  /**
   * Resolve the parent treePath for the given {@link FNITreePathCacheCompositeKey}, which contains
   * information to the corresponding flow node instance.
   *
   * <p>When the flow scope and process instance key are equal, the PI key is returned as treePath,
   * as it corresponds to a FNI on root level.
   *
   * <p>When the cache doesn't contain (as it might be evicted) the parent treePath for a given flow
   * node instance, the implementation should be able to resolve it somehow else.
   *
   * <p>If the treePath can't be resolved the process instance key should be returned.
   *
   * @param compositeKey a composite key that contains information used to resolve the parent tree
   *     path for the flow node instance
   * @return the parent treePath of the flow node instance
   * @throws IllegalArgumentException when the flow node instance record doesn't correspond to a
   *     supported partition
   */
  String resolveParentTreePath(final FNITreePathCacheCompositeKey compositeKey);

  /**
   * Cache the treePath together with the given key part of the {@link
   * FNITreePathCacheCompositeKey}.
   *
   * <p>Used for flow node instances that are a container element, like process, sub-process, event
   * sub-process, etc. This allows to resolve the treePath as parent treePath later on corresponding
   * children's.
   *
   * @param compositeKey a composite key that contains information of flow node instance
   * @param treePath the treePath of the flow node instance
   */
  void cacheTreePath(FNITreePathCacheCompositeKey compositeKey, String treePath);
}
