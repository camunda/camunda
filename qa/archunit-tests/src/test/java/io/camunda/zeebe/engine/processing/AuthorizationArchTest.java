/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.job.DefaultJobCommandPreconditionGuard;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandPreconditionChecker;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine.processing..",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class AuthorizationArchTest {

  @ArchTest()
  public static final ArchRule PROCESSOR_CHECKS_AUTHORIZATION =
      classes()
          .that(processCommands())
          .and(areNotExcludedFromAuthorizationChecks())
          .should(checkAuthorization());

  /**
   * We need to verify these classes explicitly as they are not a TypedRecordProcessor. These
   * classes take care of authorization checks for job and user task processors. By making sure we
   * call the isAuthorized method on these classes, in combination with the other rule we can ensure
   * authorization checks are performed.
   */
  @ArchTest()
  public static final ArchRule DELEGATED_CLASSES_CHECK_AUTHORIZATION =
      classes().that(areDelegatedToCheckAuthorizations()).should(checkAuthorization());

  private static DescribedPredicate<JavaClass> processCommands() {
    return new DescribedPredicate<>("process commands") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return Predicates.implement(TypedRecordProcessor.class)
            // Not all processors use the TypedRecordProcessor interface. We also need to check
            // the CommandProcessor and the UserTaskCommandProcessor interfaces.
            .or(Predicates.implement(CommandProcessor.class))
            .or(Predicates.implement(UserTaskCommandProcessor.class))
            .test(javaClass);
      }
    };
  }

  private static DescribedPredicate<JavaClass> areNotExcludedFromAuthorizationChecks() {
    return new DescribedPredicate<>("are not excluded from authorization checks") {
      @Override
      public boolean test(final JavaClass javaClass) {
        // Not all processors need to check authorization. We need to exclude those processors.
        return !javaClass.isAnnotatedWith(ExcludeAuthorizationCheck.class);
      }
    };
  }

  private static ArchCondition<JavaClass> checkAuthorization() {
    return new ArchCondition<>("check authorization") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        // The processor should directly check authorizations
        ArchConditions.callMethod(
                AuthorizationCheckBehavior.class, "isAuthorized", AuthorizationRequest.class)
            // Or the processor should have delegated authorization to the JobUpdateBehaviour
            .or(
                ArchConditions.callMethod(
                    JobUpdateBehaviour.class, "isAuthorized", TypedRecord.class, JobRecord.class))
            // Or the processor should have delegated authorization to the
            // DefaultJobCommandPreconditionGuard
            .or(
                ArchConditions.callMethod(
                    DefaultJobCommandPreconditionGuard.class,
                    "onCommand",
                    TypedRecord.class,
                    CommandControl.class))
            // Or the processor should have delegated authorization to the
            // UserTaskCommandPreconditionChecker
            .or(
                ArchConditions.callMethod(
                    UserTaskCommandPreconditionChecker.class, "check", TypedRecord.class))
            // Or the processor should have delegate authorization to the PermissionsBehavior
            .or(
                ArchConditions.callMethod(
                    PermissionsBehavior.class, "isAuthorized", TypedRecord.class))
            .or(
                ArchConditions.callMethod(
                    PermissionsBehavior.class,
                    "isAuthorized",
                    TypedRecord.class,
                    PermissionType.class))
            .check(item, events);
      }
    };
  }

  private static DescribedPredicate<JavaClass> areDelegatedToCheckAuthorizations() {
    return new DescribedPredicate<>("process commands") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return Predicates.assignableFrom(JobUpdateBehaviour.class)
            .or(Predicates.assignableFrom(DefaultJobCommandPreconditionGuard.class))
            .or(Predicates.assignableFrom(UserTaskCommandPreconditionChecker.class))
            .or(Predicates.assignableFrom(PermissionsBehavior.class))
            .test(javaClass);
      }
    };
  }
}
