/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;

/**
 * Every concrete {@link UnifiedRecordValue} that is {@link StorageOrdinalRelated} must implement
 * {@link MutableStorageOrdinal}.
 *
 * <p>Engine code sets the storage ordinal on a command value through {@link MutableStorageOrdinal}
 * without knowing the concrete record. A record that carries an ordinal but lacks the mixin is
 * skipped by that code and its records are routed to the main index. There is no whitelist, because
 * no such record can be exempt.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.protocol.impl.record.value..",
    importOptions = DoNotIncludeTestsOrTestJars.class)
final class MutableStorageOrdinalArchTest {

  @ArchTest
  static final ArchRule STORAGE_ORDINAL_RELATED_RECORDS_IMPLEMENT_MUTABLE_STORAGE_ORDINAL =
      classes().that(carryAStorageOrdinal()).should(implementMutableStorageOrdinal());

  private static DescribedPredicate<JavaClass> carryAStorageOrdinal() {
    return new DescribedPredicate<>(
        "are concrete UnifiedRecordValue subclasses that implement StorageOrdinalRelated") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return !javaClass.isInterface()
            && !javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
            && Predicates.assignableTo(UnifiedRecordValue.class).test(javaClass)
            && Predicates.implement(StorageOrdinalRelated.class).test(javaClass);
      }
    };
  }

  private static ArchCondition<JavaClass> implementMutableStorageOrdinal() {
    return new ArchCondition<>("implement MutableStorageOrdinal") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        if (!Predicates.implement(MutableStorageOrdinal.class).test(item)) {
          events.add(
              violated(
                  item,
                  String.format(
                      "Class '%s' carries a storage ordinal but does not implement"
                          + " MutableStorageOrdinal. Implement it so engine code can set the"
                          + " ordinal on the record without knowing its type.",
                      item.getFullName())));
        }
      }
    };
  }
}
