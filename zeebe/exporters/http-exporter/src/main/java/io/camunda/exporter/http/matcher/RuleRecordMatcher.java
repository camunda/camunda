/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.matcher;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.event.ruler.Machine;

/**
 * RuleRecordMatcher is responsible for matching records against a set of rules defined in JSON
 * format. It uses the <a href="https://github.com/aws/event-ruler">Amazon Event Ruler</a> library
 * to evaluate the rules against incoming records.
 */
public class RuleRecordMatcher {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final Machine machine;

  public RuleRecordMatcher(final List<String> rules) {
    machine = createRulesMachine(rules);
  }

  private Machine createRulesMachine(final List<String> rules) {
    final Machine machine = Machine.builder().build();
    rules.forEach(
        rule -> {
          try {
            // Fix: Should support proper names for rules via configuration
            machine.addRule(UUID.randomUUID().toString(), rule);
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
        });
    return machine;
  }

  public boolean matches(final String recordJson) {
    try {
      final var rules = machine.rulesForJSONEvent(recordJson);
      log.debug("Rules matched JSON event: {}", rules);
      return !rules.isEmpty();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
