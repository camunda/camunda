/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

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
import io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCompleteProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionAcknowledgeProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionContinueProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionFinishProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.job.DefaultJobCommandPreconditionGuard;
import io.camunda.zeebe.engine.processing.job.JobBatchActivateProcessor;
import io.camunda.zeebe.engine.processing.job.JobCancelProcessor;
import io.camunda.zeebe.engine.processing.job.JobRecurProcessor;
import io.camunda.zeebe.engine.processing.job.JobTimeOutProcessor;
import io.camunda.zeebe.engine.processing.job.JobYieldProcessor;
import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.message.MessageBatchExpireProcessor;
import io.camunda.zeebe.engine.processing.message.MessageExpireProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionCorrelateProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionCreateProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionDeleteProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionMigrateProcessor;
import io.camunda.zeebe.engine.processing.message.MessageSubscriptionRejectProcessor;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionCorrelateProcessor;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionCreateProcessor;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionDeleteProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceBatchActivateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceBatchTerminateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateWithResultProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessorImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.timer.TimerCancelProcessor;
import io.camunda.zeebe.engine.processing.timer.TimerTriggerProcessor;
import io.camunda.zeebe.engine.processing.usertask.UserTaskProcessor;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandPreconditionChecker;
import io.camunda.zeebe.engine.processing.usertask.processors.UserTaskCommandProcessor;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.List;

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
            // the
            // CommandProcessor and the UserTaskCommandProcessor interfaces.
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
        return !getProcessorsThatDoNotCheckAuthorization().contains(javaClass.reflect());
      }
    };
  }

  private static List<Class<?>> getProcessorsThatDoNotCheckAuthorization() {
    final List<Class<?>> processors = new ArrayList<>();
    processors.add(BpmnStreamProcessor.class);
    processors.add(CommandProcessorImpl.class);
    processors.add(DeploymentDistributeProcessor.class);
    processors.add(DeploymentDistributionCompleteProcessor.class);
    processors.add(CommandDistributionAcknowledgeProcessor.class);
    processors.add(CommandDistributionContinueProcessor.class);
    processors.add(CommandDistributionFinishProcessor.class);
    processors.add(JobBatchActivateProcessor.class); // TODO REMOVE THIS ONE
    processors.add(JobCancelProcessor.class);
    processors.add(JobRecurProcessor.class);
    processors.add(JobTimeOutProcessor.class);
    processors.add(JobYieldProcessor.class);
    processors.add(MessageBatchExpireProcessor.class);
    processors.add(MessageExpireProcessor.class);
    processors.add(MessageSubscriptionCorrelateProcessor.class);
    processors.add(MessageSubscriptionDeleteProcessor.class);
    processors.add(MessageSubscriptionMigrateProcessor.class);
    processors.add(MessageSubscriptionCreateProcessor.class);
    processors.add(MessageSubscriptionRejectProcessor.class);
    processors.add(ProcessMessageSubscriptionCorrelateProcessor.class);
    processors.add(ProcessMessageSubscriptionCreateProcessor.class);
    processors.add(ProcessMessageSubscriptionDeleteProcessor.class);
    processors.add(ProcessInstanceBatchActivateProcessor.class);
    processors.add(ProcessInstanceBatchTerminateProcessor.class);
    processors.add(ProcessInstanceCreationCreateWithResultProcessor.class);
    processors.add(TimerCancelProcessor.class);
    processors.add(TimerTriggerProcessor.class);
    processors.add(UserTaskProcessor.class);
    return processors;
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
            .check(item, events);
      }
    };
  }

  private static DescribedPredicate<JavaClass> areDelegatedToCheckAuthorizations() {
    return new DescribedPredicate<>("process commands") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return Predicates.assignableFrom(DefaultJobCommandPreconditionGuard.class)
            .or(Predicates.assignableFrom(UserTaskCommandPreconditionChecker.class))
            .test(javaClass);
      }
    };
  }
}
