/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.zeebe.scheduler.ActorScheduler;

@AnalyzeClasses(
    packages = {
      "io.camunda.application.commons.job",
      "io.camunda.application.commons.broker.client",
      "io.camunda.zeebe.gateway"
    },
    importOptions = DoNotIncludeTestsOrTestJars.class)
public class GatewayActorSchedulerArchTest {

  @ArchTest
  public static final ArchRule GATEWAY_SIDE_SHOULD_NOT_TAKE_THE_BROKER_ACTOR_SCHEDULER =
      codeUnits()
          .that()
          .areDeclaredInClassesThat()
          .doNotHaveSimpleName("GatewayFactory")
          .should(takeNoActorScheduler())
          .because(
              "gateway-side actors must run on the GatewayActorScheduler, which is separate from"
                  + " the broker's ActorScheduler when the gateway is embedded");

  private static ArchCondition<JavaCodeUnit> takeNoActorScheduler() {
    return new ArchCondition<>("not take an ActorScheduler parameter") {
      @Override
      public void check(final JavaCodeUnit codeUnit, final ConditionEvents events) {
        if (codeUnit.getRawParameterTypes().stream()
            .anyMatch(type -> type.isAssignableTo(ActorScheduler.class))) {
          events.add(SimpleConditionEvent.violated(codeUnit, codeUnit.getDescription()));
        }
      }
    };
  }
}
