/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;

public final class RecordProtocolVersionFilter implements MetadataFilter {

  @Override
  public boolean applies(final RecordMetadata m) {
    if (m.getProtocolVersion() > Protocol.PROTOCOL_VERSION) {
      throw new RuntimeException(
          String.format(
              "Cannot handle event with version newer "
                  + "than what is implemented by broker (%d > %d)",
              m.getProtocolVersion(), Protocol.PROTOCOL_VERSION));
    }

    return true;
  }
}
