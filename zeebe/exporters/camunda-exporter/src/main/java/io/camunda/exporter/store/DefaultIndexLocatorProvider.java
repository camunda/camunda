/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.OrdinalKeyBased;

public class DefaultIndexLocatorProvider implements IndexLocatorProvider {
  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    return DefaultIndexLocator.INSTANCE;
  }

  @Override
  public IndexLocator createIndexLocator(final OrdinalKeyBased ordinalKeyBased) {
    return DefaultIndexLocator.INSTANCE;
  }
}
