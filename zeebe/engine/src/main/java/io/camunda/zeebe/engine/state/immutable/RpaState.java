/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.PersistedRpa;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface RpaState {
  /**
   * Query RPAs by the given rpa id and return the latest version of the rpa.
   *
   * @param rpaId the id of the rpa
   * @param tenantId the id of the tenant
   * @return the latest version of the rpa, or {@link Optional#empty()} if no rpa is deployed with
   *     the given id
   */
  Optional<PersistedRpa> findLatestRpaById(DirectBuffer rpaId, final String tenantId);

  /**
   * Query RPAs by the given rpa key and return the rpa.
   *
   * @param rpaKey the key of the rpa
   * @param tenantId the id of the tenant
   * @return the rpa, or {@link Optional#empty()} if no rpa is deployed with the given key
   */
  Optional<PersistedRpa> findRpaByKey(long rpaKey, final String tenantId);

  /**
   * Query RPAs by the given rpa id and deployment key and return the rpa.
   *
   * @param rpaId the id of the rpa
   * @param deploymentKey the key of the deployment the rpa was deployed with
   * @param tenantId the id of the tenant
   * @return the rpa, or {@link Optional#empty()} if no rpa with the given id was deployed with
   *     the given deployment
   */
  Optional<PersistedRpa> findRpaByIdAndDeploymentKey(
      DirectBuffer rpaId, long deploymentKey, final String tenantId);

  /**
   * Query RPAs by the given rpa id and version tag and return the rpa.
   *
   * @param rpaId the id of the rpa
   * @param versionTag the version tag of the rpa
   * @param tenantId the id of the tenant
   * @return the rpa, or {@link Optional#empty()} if no rpa with the given id and version tag is
   *     deployed
   */
  Optional<PersistedRpa> findRpaByIdAndVersionTag(
      DirectBuffer rpaId, String versionTag, final String tenantId);

  /**
   * Gets the next version a rpa of a given id will receive. This is used, for example, when a new
   * deployment is done. Using this method we decide the version the newly deployed rpa receives.
   *
   * @param rpaId the id of the rpa
   */
  int getNextRpaVersion(String rpaId, String tenantId);

  void clearCache();
}
