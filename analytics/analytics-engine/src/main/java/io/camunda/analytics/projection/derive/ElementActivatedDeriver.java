/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.function.Consumer;

/** Emits an {@code ACTIVATED} fact (structural fields only — no duration yet) on activation. */
public final class ElementActivatedDeriver implements FactDeriver {

  private final Consumer<Fact> facts;

  public ElementActivatedDeriver(final Consumer<Fact> facts) {
    this.facts = facts;
  }

  @Override
  public void derive(final SourceRecord source) {
    final ProcessInstanceRecord value = (ProcessInstanceRecord) source.record().getValue();
    facts.accept(ElementFacts.base(source, value, Transition.ACTIVATED).build());
  }
}
