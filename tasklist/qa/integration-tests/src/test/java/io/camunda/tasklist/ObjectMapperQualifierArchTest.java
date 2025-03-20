/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@AnalyzeClasses(
    packages = "io.camunda.tasklist",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ObjectMapperQualifierArchTest {

  @ArchTest
  public static final ArchRule AUTOWIRED_OBJECT_MAPPER_FIELDS_SHOULD_HAVE_QUALIFIER =
      fields()
          .that()
          .areAnnotatedWith(Autowired.class)
          .and()
          .haveRawType(ObjectMapper.class)
          .should(
              new ArchCondition<>("have @Qualifier(\"tasklistObjectMapper\")") {
                @Override
                public void check(final JavaField field, final ConditionEvents events) {
                  final boolean hasQualifier = field.isAnnotatedWith(Qualifier.class);
                  if (!hasQualifier) {
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            "Field "
                                + field.getFullName()
                                + "in class "
                                + field.getOwner().getFullName()
                                + " is missing @Qualifier(\"tasklistObjectMapper\")"));
                  } else if (!"tasklistObjectMapper"
                      .equals(field.getAnnotationOfType(Qualifier.class).value())) {
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            "Field "
                                + field.getFullName()
                                + "in class "
                                + field.getOwner().getFullName()
                                + " has @Qualifier(\"%s\")"
                                    .formatted(
                                        field.getAnnotationOfType(Qualifier.class).value())));
                  }
                }
              });
}
