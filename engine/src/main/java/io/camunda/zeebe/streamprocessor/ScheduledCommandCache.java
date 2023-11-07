/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.protocol.record.intent.Intent;

public interface ScheduledCommandCache {
  void add(final Intent intent, final long key);

  boolean isCached(final Intent intent, final long key);

  void remove(final Intent intent, final long key);

  interface ScheduledCommandCacheChanges {

    void persist();
  }

  interface StageableScheduledCommandCache extends ScheduledCommandCache {
    StagedScheduledCommandCache stage();
  }

  interface StagedScheduledCommandCache
      extends ScheduledCommandCache, ScheduledCommandCacheChanges {}
}
