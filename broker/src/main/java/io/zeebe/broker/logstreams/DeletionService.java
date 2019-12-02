/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams;

import java.nio.file.Path;

/**
 * Implementations are called whenever snapshots have been purged, indicating that the data they
 * required can now be removed from the logstream.
 *
 * <p>NOTE: this should be part of a larger refactor; the purpose of this interface is practically
 * obvious, but conceptually strange
 */
public interface DeletionService {

  /**
   * Called to notify that a snapshot has been re
   *
   * @param position
   * @param directory
   */
  void delete(long position, Path directory);
}
