/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;

/**
 * Enforces that SQL mapper write methods ({@code insert*}, {@code update*}, {@code execute*},
 * {@code delete*}) are only called from within {@code io.camunda.db.rdbms.write.**}.
 *
 * <p>All RDBMS writes must flow through {@code ExecutionQueue} for batching and transaction
 * management. Direct mapper write calls from outside the write layer bypass these guarantees.
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
          .haveNameMatching("^(insert|update|execute|delete).*")
          .should()
          .onlyBeCalled()
          .byClassesThat()
          .resideInAPackage("io.camunda.db.rdbms.write..")
          .because(
              "All RDBMS write operations must flow through ExecutionQueue"
                  + " (io.camunda.db.rdbms.write.queue.ExecutionQueue) for batching and"
                  + " transaction management. Direct mapper write calls from outside the write"
                  + " layer bypass atomicity guarantees and metrics collection.");
}
