/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.service.db.DatabaseConstants;
import java.util.Locale;

public abstract class ProcessInstanceArchiveIndex<TBuilder> extends ProcessInstanceIndex<TBuilder> {

  public ProcessInstanceArchiveIndex(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  // This needs to be done separately to the logic of the constructor, because the non-static method
  // getIndexPrefix()
  // will get overridden when a subclass such as EventProcessInstanceIndex is being instantiated
  public static String constructIndexName(final String processInstanceIndexKey) {
    return DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX
        + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  @Override
  protected String getIndexPrefix() {
    return DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }
}
