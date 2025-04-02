/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;

@AnalyzeClasses(packages = "io.camunda.zeebe", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  public static final ArchRule RULE_ENGINE_CLASSES_MUST_NOT_DEPEND_ON_STREAMPROCESSOR_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage("io.camunda.zeebe.engine..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.zeebe.stream.impl..");

  @ArchTest
  public static final ArchRule RULE_ENGINE_CLASSES_MUST_NOT_DEPEND_ON_SCHEDULER_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage("io.camunda.zeebe.engine..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.zeebe.scheduler..");

  @ArchTest
  public static final ArchRule TASK_CLASSES_MUST_NOT_DEPEND_ON_STATE_PACKAGE =
      noClasses()
          .that(new MethodReferenceImplementsTask())
          .or()
          .implement(io.camunda.zeebe.stream.api.scheduling.Task.class)
          .or()
          .areAssignableTo(io.camunda.zeebe.stream.api.scheduling.Task.class)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.zeebe.stream.api.state..");

  /**
   * This predicate checks if a method reference meets exactly the signature of the task interface.
   * E.g. BatchOperationExecutionScheduler uses a method reference to execute to pass it as a Task.
   */
  private static class MethodReferenceImplementsTask extends DescribedPredicate<JavaClass> {
    MethodReferenceImplementsTask() {
      super(
          "implement or are assignable to Task interface or use lambdas implementing Task interface");
    }

    @Override
    public boolean test(final JavaClass javaClass) {
      for (final JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
        for (final var call : codeUnit.getMethodCallsFromSelf()) {

          // This is exactly the signature of the Task interface
          if (call.getTargetOwner().isAssignableTo(TaskResultBuilder.class)
              && codeUnit.getRawReturnType().isAssignableTo(TaskResult.class)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
