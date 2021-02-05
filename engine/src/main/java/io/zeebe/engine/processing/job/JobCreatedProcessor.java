/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;

/**
 * @deprecated Kept for testing. Should be removed as soon as there is a way to call the event
 *     applier, at which time this class will become obsolete because we can use just a generic
 *     forwarder to the event applier
 */
@Deprecated
public final class JobCreatedProcessor implements TypedRecordProcessor<JobRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public JobCreatedProcessor(final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void processRecord(
      final TypedRecord<JobRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final long elementInstanceKey = record.getValue().getElementInstanceKey();
    if (elementInstanceKey > 0) {
      final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

      if (elementInstance != null) {
        elementInstance.setJobKey(record.getKey());
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }
}
