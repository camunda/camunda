/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.engine.state.immutable.TriggeringRecordMetadataState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface MutableTriggeringRecordMetadataState extends TriggeringRecordMetadataState {

  void store(final long eventKey, final TriggeringRecordMetadata metadata);

  void remove(final long eventKey, final TriggeringRecordMetadata metadata);

  void remove(final long eventKey, final ValueType valueType, final Intent intent);
}
