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
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.db.rdbms.write.service.RdbmsWriter;

/**
 * Enforces the RDBMS write-path contract:
 *
 * <ol>
 *   <li>SQL mapper write methods ({@code insert*}, {@code update*}, {@code execute*}, {@code
 *       delete*}, {@code upsert*}) must only be called from within {@code
 *       io.camunda.db.rdbms.write.**}.
 *   <li>{@link RdbmsWriter} implementations must not call mapper read methods directly — reads must
 *       go through a {@code *DbReader}.
 * </ol>
 */
@AnalyzeClasses(packages = "io.camunda.db.rdbms", importOptions = DoNotIncludeTestsOrTestJars.class)
class RdbmsExecutionQueueArchTest {

  @ArchTest
  static final ArchRule MAPPER_WRITE_METHODS_MUST_ONLY_BE_CALLED_FROM_WRITE_LAYER =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("io.camunda.db.rdbms.sql..")
          .and()
          .haveNameMatching("^(insert|update|execute|delete|upsert).*")
          .should()
          .onlyBeCalled()
          .byClassesThat()
          .resideInAPackage("io.camunda.db.rdbms.write..")
          .because(
              "All RDBMS write operations must flow through the write layer"
                  + " (io.camunda.db.rdbms.write.**) for batching and transaction management."
                  + " Direct mapper write calls from outside the write layer bypass atomicity"
                  + " guarantees and metrics collection.");

  @ArchTest
  static final ArchRule RDBMS_WRITERS_MUST_NOT_CALL_MAPPER_READ_METHODS =
      ArchRuleDefinition.classes()
          .that()
          .implement(RdbmsWriter.class)
          .should(notCallMapperReadMethods())
          .because(
              "RdbmsWriter implementations must queue all operations through ExecutionQueue"
                  + " and must not read from mappers directly."
                  + " Use a *DbReader for any read-before-write pattern instead.");

  private static ArchCondition<JavaClass> notCallMapperReadMethods() {
    return new ArchCondition<>("not call mapper read methods directly") {
      @Override
      public void check(final JavaClass writerClass, final ConditionEvents events) {
        writerClass.getMethodCallsFromSelf().stream()
            .filter(
                call ->
                    call.getTargetOwner().getPackageName().startsWith("io.camunda.db.rdbms.sql"))
            .filter(call -> call.getName().matches("^(search|count|find|get|select).*"))
            .forEach(
                call ->
                    events.add(
                        violated(
                            writerClass,
                            writerClass.getSimpleName()
                                + " calls read mapper method "
                                + call.getTargetOwner().getSimpleName()
                                + "."
                                + call.getName()
                                + " at "
                                + call.getSourceCodeLocation()
                                + " — use a *DbReader instead")));
      }
    };
  }
}
