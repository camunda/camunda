/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import java.util.Set;
import org.springframework.core.GenericTypeResolver;

/**
 * Every {@link TypedRecordProcessor} whose command value type implements {@link
 * ProcessInstanceRelated} is gated by the primary suspension gate in {@code Engine.process} (see
 * #57521). Such a processor must either:
 *
 * <ul>
 *   <li>explicitly declare its suspension behavior by implementing {@link SuspensionAware} and
 *       overriding {@code suspensionBehavior(...)} (PROCESS/REJECT/BUFFER — including
 *       intent-dependent overrides), or
 *   <li>be listed in {@link #BUFFER_WHITELIST} when it intentionally relies on the gate's
 *       origin-based default (external commands rejected, internal commands buffered) instead of
 *       overriding the method.
 * </ul>
 *
 * <p>This ensures every process-instance related command processor makes a conscious, documented
 * suspension classification decision instead of silently falling back to the default.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine.processing",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class SuspensionAwareArchTest {

  @ArchTest
  public static final ArchRule PROCESS_INSTANCE_RELATED_PROCESSORS_DECLARE_SUSPENSION_BEHAVIOR =
      classes()
          .that(processProcessInstanceRelatedCommands())
          .should(declareSuspensionBehaviorOrBeWhitelisted());

  /**
   * Processors that intentionally rely on the gate's origin-based default (see {@link
   * SuspensionCheck}) without overriding {@code suspensionBehavior}. Every entry's rationale is
   * documented on the issue (#57521); non-obvious ones are annotated inline below.
   */
  private static final Set<String> BUFFER_WHITELIST =
      Set.of(
          "ProcessMessageSubscriptionCreateProcessor",
          "ProcessMessageSubscriptionCorrelateProcessor",
          "ProcessMessageSubscriptionDeleteProcessor",
          "MessageSubscriptionCreateProcessor",
          "MessageSubscriptionCorrelateProcessor",
          "MessageSubscriptionDeleteProcessor",
          "MessageSubscriptionMigrateProcessor",
          "MessageSubscriptionRejectProcessor",
          "ConditionalSubscriptionTriggerProcessor",
          "ProcessInstanceBatchActivateProcessor",
          "ProcessInstanceBusinessIdAssignProcessor",
          "TimerCancelProcessor",
          "JobRecurAfterBackoffProcessor",
          "ProcessInstanceCreationCreateProcessor",
          "ProcessInstanceCreationCreateWithAwaitingResultProcessor",
          // TODO: agentic-feature owner to confirm the Agent* classification
          "AgentInstanceCreateProcessor",
          "AgentInstanceUpdateProcessor",
          "AgentInstanceCompleteProcessor",
          "AgentHistoryCreateProcessor",
          "AgentHistoryCommitProcessor",
          "AgentHistoryDiscardProcessor");

  private static DescribedPredicate<JavaClass> processProcessInstanceRelatedCommands() {
    return new DescribedPredicate<>(
        "are concrete TypedRecordProcessor implementations for a ProcessInstanceRelated command"
            + " value") {
      @Override
      public boolean test(final JavaClass javaClass) {
        if (javaClass.isInterface()
            || javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
            || !Predicates.implement(TypedRecordProcessor.class).test(javaClass)) {
          return false;
        }
        final Class<?> valueType = resolveCommandValueType(javaClass.reflect());
        if (valueType == null) {
          // Fail loudly rather than silently skipping the processor: a guard that under-matches is
          // worse than no guard.
          throw new IllegalStateException(
              "Could not resolve the command value type of TypedRecordProcessor '"
                  + javaClass.getName()
                  + "'. The suspension rule would silently skip it; extend resolveCommandValueType"
                  + " to handle its type hierarchy.");
        }
        return ProcessInstanceRelated.class.isAssignableFrom(valueType);
      }
    };
  }

  /**
   * Resolves the {@code T} of the {@code TypedRecordProcessor<T>} that {@code clazz} implements,
   * following superclasses and transitively-extended interfaces (e.g. {@code
   * DistributedTypedRecordProcessor}) and binding type variables. Returns {@code null} only when
   * the argument genuinely can't be resolved to a concrete class.
   */
  private static Class<?> resolveCommandValueType(final Class<?> clazz) {
    return GenericTypeResolver.resolveTypeArgument(clazz, TypedRecordProcessor.class);
  }

  private static ArchCondition<JavaClass> declareSuspensionBehaviorOrBeWhitelisted() {
    return new ArchCondition<>(
        "override suspensionBehavior(...) or be listed in the BUFFER_WHITELIST") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        final boolean overridesSuspensionBehavior =
            Predicates.implement(SuspensionAware.class).test(item)
                && item.getMethods().stream()
                    .anyMatch(method -> method.getName().equals("suspensionBehavior"));
        final boolean isWhitelisted = BUFFER_WHITELIST.contains(item.getSimpleName());
        if (!overridesSuspensionBehavior && !isWhitelisted) {
          events.add(
              violated(
                  item,
                  String.format(
                      "Class '%s' processes a ProcessInstanceRelated command but does not"
                          + " implement SuspensionAware and is not listed in the"
                          + " BUFFER_WHITELIST of SuspensionAwareArchTest. Either override"
                          + " suspensionBehavior(...) or add it to the whitelist if it should"
                          + " keep the default BUFFER behavior.",
                      item.getSimpleName())));
        }
      }
    };
  }
}
