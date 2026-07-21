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
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationService;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationHelper;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionAuthorizationBehavior;
import io.camunda.zeebe.engine.processing.resource.TenantAwareDeletionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskAuthorizationCheck;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.function.Function;

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
            // Not all processors use the TypedRecordProcessor interface. We also need to check the
            // UserTaskCommandProcessor interface.
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
            .or(
                ArchConditions.callMethod(
                    AuthorizationCheckBehavior.class,
                    "isAnyAuthorized",
                    AuthorizationRequest[].class))
            .or(
                ArchConditions.callMethod(
                    AuthorizationCheckBehavior.class,
                    "isAuthorizedOrInternalCommand",
                    AuthorizationRequest.class))
            .or(
                ArchConditions.callMethod(
                    AuthorizationCheckBehavior.class,
                    "isAnyAuthorizedOrInternalCommand",
                    AuthorizationRequest[].class))
            // Or the processor should have delegated authorization to the JobUpdateBehaviour
            .or(
                ArchConditions.callMethod(
                    JobUpdateBehaviour.class, "isAuthorized", TypedRecord.class, JobRecord.class))
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
            .or(
                ArchConditions.callMethod(
                    PermissionsBehavior.class,
                    "isAuthorized",
                    TypedRecord.class,
                    AuthorizationResourceType.class,
                    PermissionType.class))
            .or(
                ArchConditions.callMethod(
                    PermissionsBehavior.class,
                    "isAuthorized",
                    TypedRecord.class,
                    AuthorizationResourceType.class,
                    PermissionType.class,
                    String.class))
            .or(
                ArchConditions.callMethod(
                    PermissionsBehavior.class,
                    "isAuthorizedWithResourceIdentifiers",
                    TypedRecord.class,
                    AuthorizationResourceType.class,
                    PermissionType.class,
                    String.class))
            .or(
                ArchConditions.callMethod(
                    ProcessInstanceCreationHelper.class,
                    "isAuthorized",
                    TypedRecord.class,
                    DeployedProcess.class))
            .or(
                ArchConditions.callMethod(
                    TenantAwareDeletionBehavior.class,
                    "forEachAuthorizedTenantUntilDeleted",
                    TypedRecord.class,
                    Function.class))
            .or(
                ArchConditions.callMethod(
                    ResourceDeletionAuthorizationBehavior.class,
                    "checkAuthorizationForHistoryDeletion",
                    TypedRecord.class))
            .or(
                ArchConditions.callMethod(
                    AuthorizationCheckPort.class,
                    "check",
                    CamundaAuthentication.class,
                    RequiredAuthorization.class))
            .or(
                ArchConditions.callMethod(
                    AuthorizationService.class,
                    "check",
                    CamundaAuthentication.class,
                    RequiredAuthorization.class))
            // Or the processor should delegate the skip-logic + check to CslAuthorizationCheck
            .or(
                ArchConditions.callMethod(
                    CslAuthorizationCheck.class,
                    "check",
                    TypedRecord.class,
                    RequiredAuthorization.class,
                    Object.class,
                    Rejection.class))
            .or(
                ArchConditions.callMethod(
                    CslAuthorizationCheck.class,
                    "check",
                    TypedRecord.class,
                    RequiredAuthorization.class,
                    Object.class,
                    Rejection.class,
                    Function.class))
            .or(
                ArchConditions.callMethod(
                    CslAuthorizationCheck.class,
                    "checkForDistributedCommand",
                    TypedRecord.class,
                    RequiredAuthorization.class,
                    Object.class,
                    Rejection.class))
            // Or the class should delegate an already-resolved-principal check to
            // CslAuthorizationCheck.checkAuth (used by UserTaskAuthorizationCheck for the
            // per-alternative resource-id and property-scoped grants)
            .or(
                ArchConditions.callMethod(
                    CslAuthorizationCheck.class,
                    "checkAuth",
                    CamundaAuthentication.class,
                    RequiredAuthorization.class))
            .or(
                ArchConditions.callMethod(
                    CslAuthorizationCheck.class,
                    "checkAuth",
                    CamundaAuthentication.class,
                    RequiredAuthorization.class,
                    Object.class))
            // Or the UserTask processors should delegate the OR-orchestration to
            // UserTaskAuthorizationCheck
            .or(
                ArchConditions.callMethod(
                    UserTaskAuthorizationCheck.class,
                    "check",
                    TypedRecord.class,
                    UserTaskRecord.class,
                    UserTaskAuthorizationCheck.Alt[].class))
            .check(item, events);
      }
    };
  }

  private static DescribedPredicate<JavaClass> areDelegatedToCheckAuthorizations() {
    return new DescribedPredicate<>("process commands") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return Predicates.assignableFrom(JobUpdateBehaviour.class)
            .or(Predicates.assignableFrom(PermissionsBehavior.class))
            .or(Predicates.assignableFrom(UserTaskAuthorizationCheck.class))
            .test(javaClass);
      }
    };
  }
}
