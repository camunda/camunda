/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.springframework.core.GenericTypeResolver;

/**
 * Every {@link TypedRecordProcessor} whose command value type implements {@link
 * StorageOrdinalRelated} must implement {@link StorageOrdinalAware}.
 *
 * <p>The engine restores the storage ordinal on the value of a client command through {@link
 * StorageOrdinalAware}. A processor without it fails at engine start, when the engine rejects its
 * registration. This rule moves that failure to test time. There is no whitelist, because no such
 * processor can be exempt.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine.processing",
    importOptions = DoNotIncludeTestsOrTestJars.class)
final class StorageOrdinalAwareArchTest {

  @ArchTest
  public static final ArchRule STORAGE_ORDINAL_RELATED_PROCESSORS_IMPLEMENT_STORAGE_ORDINAL_AWARE =
      classes().that(processStorageOrdinalRelatedCommands()).should(implementStorageOrdinalAware());

  private static DescribedPredicate<JavaClass> processStorageOrdinalRelatedCommands() {
    return new DescribedPredicate<>(
        "are concrete TypedRecordProcessor implementations for a StorageOrdinalRelated command"
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
                  + "'. The storage ordinal rule would silently skip it; extend"
                  + " resolveCommandValueType to handle its type hierarchy.");
        }
        return StorageOrdinalRelated.class.isAssignableFrom(valueType);
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

  private static ArchCondition<JavaClass> implementStorageOrdinalAware() {
    return new ArchCondition<>("implement StorageOrdinalAware") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        if (!Predicates.implement(StorageOrdinalAware.class).test(item)) {
          events.add(
              violated(
                  item,
                  String.format(
                      "Class '%s' processes a StorageOrdinalRelated command but does not implement"
                          + " StorageOrdinalAware. Implement it so the engine can restore the"
                          + " storage ordinal on the command value; the engine rejects the"
                          + " registration of this processor otherwise.",
                      item.getFullName())));
        }
      }
    };
  }
}
