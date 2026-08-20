/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.service.RdbmsWriter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces that every concrete {@link RdbmsWriter} implementation is registered in {@link
 * RdbmsWriters}.
 *
 * <p>{@link RdbmsWriters} is the single wiring point for all exporter-driven writers. An
 * unregistered writer silently receives no flush callbacks and is never accessible to the exporter.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class RdbmsWritersArchTest {

  @ArchTest
  static final ArchRule ALL_RDBMS_WRITERS_MUST_BE_REGISTERED =
      ArchRuleDefinition.classes()
          .that()
          .haveFullyQualifiedName(RdbmsWriters.class.getName())
          .should(containAllConcreteRdbmsWriters())
          .because(
              "every RdbmsWriter implementation must be registered in RdbmsWriters"
                  + " so it receives ExecutionQueue flush callbacks and is accessible"
                  + " to the exporter");

  private static ArchCondition<JavaClass> containAllConcreteRdbmsWriters() {
    return new ArchCondition<>("reference every concrete RdbmsWriter implementation") {

      private List<JavaClass> writerImplementors;

      @Override
      public void init(final Collection<JavaClass> allClasses) {
        writerImplementors =
            allClasses.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
                .filter(c -> c.isAssignableTo(RdbmsWriter.class))
                .toList();
      }

      @Override
      public void check(final JavaClass rdbmsWriters, final ConditionEvents events) {
        final Set<String> referencedTypes =
            rdbmsWriters.getDirectDependenciesFromSelf().stream()
                .map(dep -> dep.getTargetClass().getName())
                .collect(Collectors.toSet());

        for (final JavaClass writer : writerImplementors) {
          if (!referencedTypes.contains(writer.getName())) {
            events.add(
                violated(
                    rdbmsWriters,
                    writer.getSimpleName()
                        + " implements RdbmsWriter but is not registered in RdbmsWriters"
                        + " — add a writers.put("
                        + writer.getSimpleName()
                        + ".class, ...) entry"));
          }
        }
      }
    };
  }
}
