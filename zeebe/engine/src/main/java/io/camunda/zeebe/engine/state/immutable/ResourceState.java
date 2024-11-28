/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface ResourceState {
  /**
   * Query Resource by the given resource id and return the latest version of the resource.
   *
   * @param resourceId the id of the resource
   * @param tenantId the id of the tenant
   * @return the latest version of the resource, or {@link Optional#empty()} if no resource is
   *     deployed with the given id
   */
  Optional<PersistedResource> findLatestResourceById(
      DirectBuffer resourceId, final String tenantId);

  /**
   * Query Resource by the given resource key and return the resource.
   *
   * @param resourceKey the key of the resource
   * @param tenantId the id of the tenant
   * @return the resource, or {@link Optional#empty()} if no resource is deployed with the given key
   */
  Optional<PersistedResource> findResourceByKey(long resourceKey, final String tenantId);

  /**
   * Query Resource by the given resource id and deployment key and return the resource.
   *
   * @param resourceId the id of the resource
   * @param deploymentKey the key of the deployment the resource was deployed with
   * @param tenantId the id of the tenant
   * @return the resource, or {@link Optional#empty()} if no resource with the given id was deployed
   *     with the given deployment
   */
  Optional<PersistedResource> findResourceByIdAndDeploymentKey(
      DirectBuffer resourceId, long deploymentKey, final String tenantId);

  /**
   * Query Resource by the given resource id and version tag and return the resource.
   *
   * @param resourceId the id of the resource
   * @param versionTag the version tag of the resource
   * @param tenantId the id of the tenant
   * @return the resource, or {@link Optional#empty()} if no resource with the given id and version
   *     tag is deployed
   */
  Optional<PersistedResource> findResourceByIdAndVersionTag(
      DirectBuffer resourceId, String versionTag, final String tenantId);

  /**
   * Gets the next version a resource of a given id will receive. This is used, for example, when a
   * new deployment is done. Using this method we decide the version the newly deployed resource
   * receives.
   *
   * @param resourceId the id of the resource
   */
  int getNextResourceVersion(String resourceId, String tenantId);

  void clearCache();
}
