/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.keygenerator.KeyGeneratorResetRecord;
import io.camunda.zeebe.protocol.record.intent.KeyGeneratorResetIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class KeyGeneratorResetAppliedApplier
    implements TypedEventApplier<KeyGeneratorResetIntent, KeyGeneratorResetRecord> {

  private final KeyGenerator keyGenerator;

  public KeyGeneratorResetAppliedApplier(final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void applyState(final long key, final KeyGeneratorResetRecord value) {
    // Apply the reset to the key generator
    keyGenerator.overwriteNextKey(value.getNewKeyValue());
  }
}
