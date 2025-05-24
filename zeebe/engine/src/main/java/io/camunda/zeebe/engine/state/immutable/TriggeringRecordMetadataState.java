/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Optional;

public interface TriggeringRecordMetadataState {

  Optional<TriggeringRecordMetadata> findExact(
      final long eventKey, final ValueType valueType, final Intent intent);

  Optional<TriggeringRecordMetadata> findOnly(final long eventKey, final ValueType valueType);
}
