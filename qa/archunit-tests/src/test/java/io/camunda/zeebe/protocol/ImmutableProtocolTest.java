/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationRelated;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.lang.reflect.Method;
import org.immutables.value.Value;

/**
 * This test ensures that we always generate immutable variants of every protocol type, namely:
 *
 * <ul>
 *   <li>{@link Record}
 *   <li>all types extending {@link io.camunda.zeebe.protocol.record.RecordValue}, except certain
 *       meta types, such as {@link ProcessInstanceRelated} or {@link
 *       io.camunda.zeebe.protocol.record.RecordValueWithVariables}
 *   <li>all other types referenced in the above values, such as {@link
 *       io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource}
 * </ul>
 */
@AnalyzeClasses(packages = "io.camunda.zeebe.protocol.record..")
final class ImmutableProtocolTest {
  @ArchTest
  void shouldAnnotateImmutableProtocol(final JavaClasses importedClasses) {
    // given
    final ArchRule rule =
        ArchRuleDefinition.classes()
            .that()
            // lookup only interface types, ignore enums or concrete classes
            .areInterfaces()
            .and()
            // check only all the types under the record.value and subpackages
            .resideInAnyPackage("io.camunda.zeebe.protocol.record.value..")
            // also check the Record interface itself
            .or(Predicates.equivalentTo(Record.class))
            // exclude certain interfaces
            .and(DescribedPredicate.not(getExcludedClasses()))
            .should()
            .beAnnotatedWith(Value.Immutable.class)
            .andShould()
            .beAnnotatedWith(ImmutableProtocol.class)
            .andShould(new BuilderCondition());

    // then
    rule.check(importedClasses);
  }

  @ArchTest
  void shouldNotAnnotateExcludedClasses(final JavaClasses importedClasses) {
    // given
    final ArchRule rule =
        ArchRuleDefinition.classes()
            .that(getExcludedClasses())
            .should()
            .notBeAnnotatedWith(ImmutableProtocol.class)
            .orShould()
            .notBeAnnotatedWith(Value.Immutable.class);

    // then
    rule.check(importedClasses);
  }

  private DescribedPredicate<JavaClass> getExcludedClasses() {
    return Predicates.equivalentTo(ProcessInstanceRelated.class)
        .or(Predicates.equivalentTo(BatchOperationRelated.class))
        .or(Predicates.equivalentTo(TenantOwned.class));
  }

  private static final class BuilderCondition extends ArchCondition<JavaClass> {

    private BuilderCondition() {
      super(
          "declare a builder class as ImmutableProtocol#builder= which builds instances"
              + " of itself");
    }

    @Override
    public void check(final JavaClass item, final ConditionEvents events) {
      final ImmutableProtocol annotation = item.getAnnotationOfType(ImmutableProtocol.class);
      final Class<?> builderClass = annotation.builder();
      final Method buildMethod;
      try {
        buildMethod = builderClass.getDeclaredMethod("build");
      } catch (final NoSuchMethodException e) {
        events.add(violated(item, builderClass, "have a build method"));
        return;
      }

      if (!item.isAssignableFrom(buildMethod.getReturnType())) {
        final String failure =
            String.format(
                "build an object of type assignable to ([%s]) but instead builds [%s]",
                item.getName(), buildMethod.getReturnType().getName());
        events.add(violated(item, builderClass, failure));
      }
    }

    private ConditionEvent violated(
        final JavaClass annotatedClass, final Class<?> builderClass, final String failure) {
      final String message =
          String.format(
              "[%s] defines builder class ImmutableProtocol(builder=[%s]) which does not %s",
              annotatedClass.getName(), builderClass.getName(), failure);
      return SimpleConditionEvent.violated(annotatedClass, message);
    }
  }
}
