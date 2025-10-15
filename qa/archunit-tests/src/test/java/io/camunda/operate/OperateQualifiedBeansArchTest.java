/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@AnalyzeClasses(
    packages = "io.camunda.operate",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class OperateQualifiedBeansArchTest {

  private static final String OPERATE_OBJECT_MAPPER_QUALIFIER = "operateObjectMapper";

  @ArchTest
  public static final ArchRule AUTOWIRED_OBJECT_MAPPER_FIELDS_SHOULD_HAVE_QUALIFIER =
      fields()
          .that()
          .areAnnotatedWith(Autowired.class)
          .and()
          .haveRawType(ObjectMapper.class)
          .should(
              new ArchCondition<>("have @Qualifier(\"" + OPERATE_OBJECT_MAPPER_QUALIFIER + "\")") {
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
                                + " is missing @Qualifier(\""
                                + OPERATE_OBJECT_MAPPER_QUALIFIER
                                + "\")"));
                  } else if (!OPERATE_OBJECT_MAPPER_QUALIFIER.equals(
                      field.getAnnotationOfType(Qualifier.class).value())) {
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

  private static final String OPEN_SEARCH_CLIENT_QUALIFIER = "openSearchClient";

  @ArchTest
  public static final ArchRule AUTOWIRED_OS_CLIENT_FIELDS_SHOULD_HAVE_QUALIFIER =
      fields()
          .that()
          .areAnnotatedWith(Autowired.class)
          .and()
          .haveRawType(OpenSearchClient.class)
          .should(
              new ArchCondition<>("have @Qualifier(\"" + OPEN_SEARCH_CLIENT_QUALIFIER + "\")") {
                @Override
                public void check(final JavaField field, final ConditionEvents events) {
                  final boolean hasQualifier = field.isAnnotatedWith(Qualifier.class);
                  if (!hasQualifier) {
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            "Field "
                                + field.getFullName()
                                + " in class "
                                + field.getOwner().getFullName()
                                + " is missing @Qualifier(\""
                                + OPEN_SEARCH_CLIENT_QUALIFIER
                                + "\")"));
                  } else if (!field
                      .getAnnotationOfType(Qualifier.class)
                      .value()
                      .equals(OPEN_SEARCH_CLIENT_QUALIFIER)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            "Field "
                                + field.getFullName()
                                + " in class "
                                + field.getOwner().getFullName()
                                + " has @Qualifier(\"%s\")"
                                    .formatted(
                                        field.getAnnotationOfType(Qualifier.class).value())));
                  }
                }
              })
          .allowEmptyShould(true);

  @ArchTest
  public static final ArchRule AUTOWIRED_OS_CLIENT_CONSTRUCTOR_PARAMS_SHOULD_HAVE_QUALIFIER =
      constructors()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(org.springframework.stereotype.Component.class)
          .should(
              new ArchCondition<>(
                  "have OpenSearchClient parameters with @Qualifier(\""
                      + OPEN_SEARCH_CLIENT_QUALIFIER
                      + "\")") {
                @Override
                public void check(final JavaConstructor constructor, final ConditionEvents events) {
                  constructor.getParameters().stream()
                      .filter(param -> param.getRawType().isEquivalentTo(OpenSearchClient.class))
                      .forEach(
                          param -> {
                            final boolean hasQualifier = param.isAnnotatedWith(Qualifier.class);
                            if (!hasQualifier) {
                              events.add(
                                  SimpleConditionEvent.violated(
                                      constructor,
                                      "Constructor parameter "
                                          + param.getIndex()
                                          + " of type OpenSearchClient in constructor "
                                          + constructor.getFullName()
                                          + " is missing @Qualifier(\""
                                          + OPEN_SEARCH_CLIENT_QUALIFIER
                                          + "\")"));
                            } else if (!param
                                .getAnnotationOfType(Qualifier.class)
                                .value()
                                .equals(OPEN_SEARCH_CLIENT_QUALIFIER)) {
                              events.add(
                                  SimpleConditionEvent.violated(
                                      constructor,
                                      "Constructor parameter "
                                          + param.getIndex()
                                          + " of type OpenSearchClient in constructor "
                                          + constructor.getFullName()
                                          + " has @Qualifier(\"%s\")"
                                              .formatted(
                                                  param
                                                      .getAnnotationOfType(Qualifier.class)
                                                      .value())));
                            }
                          });
                }
              })
          .allowEmptyShould(true);
}
