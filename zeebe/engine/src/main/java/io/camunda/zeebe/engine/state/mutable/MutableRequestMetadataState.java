/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.RequestMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.RequestMetadataRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface MutableRequestMetadataState extends RequestMetadataState {

  void store(final RequestMetadataRecord metadataRecord);

  void remove(final RequestMetadataRecord metadataRecord);

  void remove(final long scopeKey, final ValueType valueType, final Intent intent);
}
