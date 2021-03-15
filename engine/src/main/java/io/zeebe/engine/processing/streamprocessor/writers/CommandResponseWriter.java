/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public interface CommandResponseWriter {

  CommandResponseWriter partitionId(int partitionId);

  CommandResponseWriter key(long key);

  CommandResponseWriter intent(Intent intent);

  CommandResponseWriter recordType(RecordType type);

  CommandResponseWriter valueType(ValueType valueType);

  CommandResponseWriter rejectionType(RejectionType rejectionType);

  CommandResponseWriter rejectionReason(DirectBuffer rejectionReason);

  CommandResponseWriter valueWriter(BufferWriter value);

  boolean tryWriteResponse(int requestStreamId, long requestId);
}
