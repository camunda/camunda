/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;

public class MetadataEventFilter implements EventFilter {

  protected final RecordMetadata metadata = new RecordMetadata();
  protected final MetadataFilter metadataFilter;

  public MetadataEventFilter(final MetadataFilter metadataFilter) {
    this.metadataFilter = metadataFilter;
  }

  @Override
  public boolean applies(final LoggedEvent event) {
    event.readMetadata(metadata);
    return metadataFilter.applies(metadata);
  }
}
