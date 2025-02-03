/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.conditions;

import io.camunda.search.connect.configuration.DatabaseType;
import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnDatabaseTypeCondition extends SpringBootCondition {

  @Override
  public ConditionOutcome getMatchOutcome(
      final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final Map<String, Object> attributes =
        metadata.getAnnotationAttributes(ConditionalOnDatabaseType.class.getName());
    final DatabaseType[] values = (DatabaseType[]) attributes.get("value");
    final boolean matchIfMissing = (Boolean) attributes.get("matchIfMissing");

    final String propertyValue = context.getEnvironment().getProperty("camunda.database.type");
    if (propertyValue != null) {
      for (final DatabaseType value : values) {
        if (propertyValue.equalsIgnoreCase(value.getType())) {
          return ConditionOutcome.match(
              ConditionMessage.forCondition(ConditionalOnDatabaseType.class)
                  .because(
                      "found value "
                          + propertyValue.toLowerCase()
                          + " in 'camunda.database.type' did match one of the required values "
                          + String.join(
                              ", ",
                              Arrays.stream(values)
                                  .map(DatabaseType::getType)
                                  .toArray(String[]::new))));
        }
      }

      return ConditionOutcome.noMatch(
          ConditionMessage.forCondition(ConditionalOnDatabaseType.class)
              .because(
                  "found value "
                      + propertyValue.toLowerCase()
                      + " in 'camunda.database.type' did not match any of the required values "
                      + Arrays.stream(values)
                          .map(DatabaseType::getType)
                          .reduce((a, b) -> a + ", " + b)
                          .orElse("")));
    }

    if (matchIfMissing) {
      return ConditionOutcome.match(
          ConditionMessage.forCondition(ConditionalOnDatabaseType.class)
              .because(
                  "did not find required property 'camunda.database.type'"
                      + " but matchIfMissing is set to true"));
    }
    return ConditionOutcome.noMatch(
        ConditionMessage.forCondition(ConditionalOnDatabaseType.class)
            .because("did not find required property 'camunda.database.type'"));
  }
}
