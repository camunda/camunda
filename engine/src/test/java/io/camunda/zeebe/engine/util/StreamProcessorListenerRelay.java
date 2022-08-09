/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import java.util.Collections;
import java.util.List;

final class StreamProcessorListenerRelay implements StreamProcessorListener {

  private final List<StreamProcessorListener> delegates;

  StreamProcessorListenerRelay(final List<StreamProcessorListener> delegates) {
    this.delegates = Collections.unmodifiableList(delegates);
  }

  @Override
  public void onProcessed(final TypedRecord<?> processedCommand) {
    delegates.forEach(l -> l.onProcessed(processedCommand));
  }

  @Override
  public void onSkipped(final LoggedEvent skippedRecord) {
    delegates.forEach(l -> l.onSkipped(skippedRecord));
  }
}
