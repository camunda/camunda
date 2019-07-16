/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore;

import io.atomix.cluster.MemberId;

@FunctionalInterface
public interface RestoreNodeProvider {

  /**
   * Provides a node from which to restore from; the node is guaranteed to not be the local member
   * ID.
   *
   * @return an active node from which to restore from
   */
  MemberId provideRestoreNode();
}
