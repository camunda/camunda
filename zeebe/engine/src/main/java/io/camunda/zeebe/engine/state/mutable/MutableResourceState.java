/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;

public interface MutableResourceState extends ResourceState {
  /**
   * Put the given resource in RESOURCES column family
   *
   * @param record the record of the resource
   */
  void storeResourceInResourceColumnFamily(ResourceRecord record);

  /**
   * Put the given resource in RESOURCE_BY_ID_AND_VERSION column family
   *
   * @param record the record of the resource
   */
  void storeResourceInResourceByIdAndVersionColumnFamily(ResourceRecord record);

  /**
   * Put the given resource in RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY column family
   *
   * @param record the record of the resource
   */
  void storeResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(ResourceRecord record);

  /**
   * Put the given resource in RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG column family
   *
   * @param record the record of the resource
   */
  void storeResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(ResourceRecord record);

  /**
   * Update the latest version of the resource if it is newer.
   *
   * @param record the record of the resource
   */
  void updateLatestVersion(ResourceRecord record);

  /**
   * Deletes a resource from RESOURCES column family
   *
   * @param record the record of the resource that is deleted
   */
  void deleteResourceInResourcesColumnFamily(ResourceRecord record);

  /**
   * Deletes a resource from RESOURCE_BY_ID_AND_VERSION column family
   *
   * @param record the record of the resource that is deleted
   */
  void deleteResourceInResourceByIdAndVersionColumnFamily(ResourceRecord record);

  /**
   * Deletes a resource from RESOURCE_VERSION column family
   *
   * @param record the record of the resource that is deleted
   */
  void deleteResourceInResourceVersionColumnFamily(ResourceRecord record);

  /**
   * Deletes a resource from RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY column family
   *
   * @param record the record of the resource that is deleted
   */
  void deleteResourceInResourceKeyByResourceIdAndDeploymentKeyColumnFamily(ResourceRecord record);

  /**
   * Deletes a resource from RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG column family
   *
   * @param record the record of the resource that is deleted
   */
  void deleteResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(ResourceRecord record);
}
