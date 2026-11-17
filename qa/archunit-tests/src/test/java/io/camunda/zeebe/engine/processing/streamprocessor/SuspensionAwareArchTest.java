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
 * ProcessInstanceRelated} is gated by the primary suspension gate in {@code Engine.process}. Such a
 * processor must either:
 *
 * <ul>
 *   <li>implement {@link SuspensionAware}, which forces it to return an explicit
 *       PROCESS/REJECT/BUFFER decision (the interface has no default), or
 *   <li>be listed in {@link #WHITELIST} when it processes commands unrelated to process instance
 *       state changes and therefore needs no suspension classification.
 * </ul>
 *
 * <p>This ensures every process-instance related command processor either makes a conscious,
 * documented suspension decision or is explicitly exempted.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine.processing",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class SuspensionAwareArchTest {

  @ArchTest
  public static final ArchRule PROCESS_INSTANCE_RELATED_PROCESSORS_IMPLEMENT_SUSPENSION_AWARE =
      classes()
          .that(processProcessInstanceRelatedCommands())
          .should(implementSuspensionAwareOrBeWhitelisted());

  /**
   * Fully-qualified names of processors that do not need to implement {@link SuspensionAware}
   * because they process commands unrelated to process instance state changes.
   */
  private static final Set<String> WHITELIST =
      Set.of(
          "io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionCreateProcessor",
          "io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionDeleteProcessor",
          "io.camunda.zeebe.engine.processing.message.MessageSubscriptionCreateProcessor",
          "io.camunda.zeebe.engine.processing.message.MessageSubscriptionCorrelateProcessor",
          "io.camunda.zeebe.engine.processing.message.MessageSubscriptionDeleteProcessor",
          "io.camunda.zeebe.engine.processing.message.MessageSubscriptionMigrateProcessor",
          "io.camunda.zeebe.engine.processing.message.MessageSubscriptionRejectProcessor",
          "io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateProcessor",
          "io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateWithAwaitingResultProcessor");

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

  private static ArchCondition<JavaClass> implementSuspensionAwareOrBeWhitelisted() {
    return new ArchCondition<>("implement SuspensionAware or be listed in the WHITELIST") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        final boolean implementsSuspensionAware =
            Predicates.implement(SuspensionAware.class).test(item);
        final boolean isWhitelisted = WHITELIST.contains(item.getFullName());
        if (!implementsSuspensionAware && !isWhitelisted) {
          events.add(
              violated(
                  item,
                  String.format(
                      "Class '%s' processes a ProcessInstanceRelated command but does not implement"
                          + " SuspensionAware, and is not listed in the WHITELIST of"
                          + " SuspensionAwareArchTest. Either implement SuspensionAware (returning an"
                          + " explicit PROCESS/REJECT/BUFFER decision) or add it to the whitelist if"
                          + " it processes commands unrelated to process instance state changes.",
                      item.getFullName())));
        }
      }
    };
  }
}
