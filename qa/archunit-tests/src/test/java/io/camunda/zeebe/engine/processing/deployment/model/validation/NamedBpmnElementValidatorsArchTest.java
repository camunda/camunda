/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidatorConfig;
import io.camunda.zeebe.model.bpmn.instance.NamedBpmnElement;
import io.camunda.zeebe.model.bpmn.instance.dc.Font;
import io.camunda.zeebe.model.bpmn.instance.di.Diagram;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ArchUnit test to ensure all direct sub-interfaces of {@link NamedBpmnElement} are validated in
 * {@link ZeebeConfigurationValidators}.
 *
 * <p>This test verifies that for each interface that directly extends {@link NamedBpmnElement},
 * there is a corresponding {@code NameLengthValidator} entry in the {@link
 * ZeebeConfigurationValidators#getValidators} method.
 */
@AnalyzeClasses(
    packages = {
      "io.camunda.zeebe.engine.processing.deployment.model.validation",
      "io.camunda.zeebe.model.bpmn.instance"
    },
    importOptions = ImportOption.DoNotIncludeTests.class)
public class NamedBpmnElementValidatorsArchTest {

  @ArchTest
  static final ArchRule ALL_NAMED_BPMN_ELEMENT_SUB_INTERFACES_MUST_BE_REGISTERED =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(ZeebeConfigurationValidators.class)
          .should(new AllNamedBpmnElementsAreValidatedCondition());

  private static class AllNamedBpmnElementsAreValidatedCondition extends ArchCondition<JavaClass> {

    public AllNamedBpmnElementsAreValidatedCondition() {
      super("register NameLengthValidator for all direct sub-interfaces of NamedBpmnElement");
    }

    @Override
    public void check(final JavaClass item, final ConditionEvents events) {
      // Import all classes from the BPMN model package
      final var bpmnClasses =
          new ClassFileImporter().importPackages("io.camunda.zeebe.model.bpmn.instance");

      // Find the NamedBpmnElement interface
      final JavaClass namedBpmnElement = bpmnClasses.get(NamedBpmnElement.class);

      // Find all direct sub-interfaces (interfaces that directly extend
      // NamedBpmnElement)
      final Set<JavaClass> directSubInterfaces =
          bpmnClasses.stream()
              .filter(
                  clazz ->
                      clazz.isInterface()
                          && !clazz.equals(namedBpmnElement)
                          && clazz.getRawInterfaces().contains(namedBpmnElement))
              .collect(Collectors.toSet());

      // Font is a special case from the dc package that can be excluded
      directSubInterfaces.removeIf(clazz -> clazz.isEquivalentTo(Font.class));
      directSubInterfaces.removeIf(clazz -> clazz.isEquivalentTo(Diagram.class));

      // Use reflection to call getValidators and extract registered types
      final var validators =
          ZeebeConfigurationValidators.getValidators(BpmnValidatorConfig.builder().build());

      final Set<String> registeredTypes =
          validators.stream()
              .filter(NameLengthValidator.class::isInstance)
              .map(v -> ((NameLengthValidator<?>) v).getElementType().getName())
              .collect(Collectors.toSet());

      // Check that all direct sub-interfaces are registered
      final List<String> missingTypes =
          directSubInterfaces.stream()
              .filter(subInterface -> !registeredTypes.contains(subInterface.getName()))
              .map(JavaClass::getSimpleName)
              .toList();

      if (!missingTypes.isEmpty()) {
        events.add(
            violated(
                item,
                String.format(
                    "ZeebeConfigurationValidators.getValidators() must register NameLengthValidator for all direct "
                        + "sub-interfaces of NamedBpmnElement. Missing validators for: %s",
                    String.join(", ", missingTypes))));
      }
    }
  }
}
