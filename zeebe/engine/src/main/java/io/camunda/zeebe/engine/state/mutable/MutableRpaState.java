/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.RpaState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.RpaRecord;

public interface MutableRpaState extends RpaState {
  /**
   * Put the given rpa in RPAS column family
   *
   * @param record the record of the rpa
   */
  void storeRpaInRpaColumnFamily(RpaRecord record);

  /**
   * Put the given rpa in RPA_BY_ID_AND_VERSION column family
   *
   * @param record the record of the rpa
   */
  void storeRpaInRpaByIdAndVersionColumnFamily(RpaRecord record);

  /**
   * Put the given rpa in RPA_KEY_BY_RPA_ID_AND_DEPLOYMENT_KEY column family
   *
   * @param record the record of the rpa
   */
  void storeRpaInRpaKeyByRpaIdAndDeploymentKeyColumnFamily(RpaRecord record);

  /**
   * Put the given rpa in RPA_KEY_BY_RPA_ID_AND_VERSION_TAG column family
   *
   * @param record the record of the rpa
   */
  void storeRpaInRpaKeyByRpaIdAndVersionTagColumnFamily(RpaRecord record);

  /**
   * Update the latest version of the rpa if it is newer.
   *
   * @param record the record of the rpa
   */
  void updateLatestVersion(RpaRecord record);

  /**
   * Deletes a rpa from RPAS column family
   *
   * @param record the record of the rpa that is deleted
   */
  void deleteRpaInRpasColumnFamily(RpaRecord record);

  /**
   * Deletes a rpa from RPA_BY_ID_AND_VERSION column family
   *
   * @param record the record of the rpa that is deleted
   */
  void deleteRpaInRpaByIdAndVersionColumnFamily(RpaRecord record);

  /**
   * Deletes a rpa from RPA_VERSION column family
   *
   * @param record the record of the rpa that is deleted
   */
  void deleteRpaInRpaVersionColumnFamily(RpaRecord record);

  /**
   * Deletes a rpa from RPA_KEY_BY_RPA_ID_AND_DEPLOYMENT_KEY column family
   *
   * @param record the record of the rpa that is deleted
   */
  void deleteRpaInRpaKeyByRpaIdAndDeploymentKeyColumnFamily(RpaRecord record);

  /**
   * Deletes a rpa from RPA_KEY_BY_RPA_ID_AND_VERSION_TAG column family
   *
   * @param record the record of the rpa that is deleted
   */
  void deleteRpaInRpaKeyByRpaIdAndVersionTagColumnFamily(RpaRecord record);
}
