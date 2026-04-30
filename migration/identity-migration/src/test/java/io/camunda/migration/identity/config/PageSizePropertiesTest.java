/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PageSizePropertiesTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void defaultsAreOneHundred() {
    final var properties = new PageSizeProperties();

    assertThat(properties.getUsers())
        .isEqualTo(PageSizeProperties.DEFAULT_PAGE_SIZE)
        .isEqualTo(100);
    assertThat(properties.getGroups())
        .isEqualTo(PageSizeProperties.DEFAULT_PAGE_SIZE)
        .isEqualTo(100);
  }

  @Test
  void rejectsUsersBelowOne() {
    final var properties = new PageSizeProperties();
    properties.setUsers(0);

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("users");
  }

  @Test
  void rejectsGroupsBelowOne() {
    final var properties = new PageSizeProperties();
    properties.setGroups(0);

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("groups");
  }

  @Test
  void rejectsUsersAboveMax() {
    final var properties = new PageSizeProperties();
    properties.setUsers(PageSizeProperties.MAX_PAGE_SIZE + 1);

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("users");
  }

  @Test
  void rejectsGroupsAboveMax() {
    final var properties = new PageSizeProperties();
    properties.setGroups(PageSizeProperties.MAX_PAGE_SIZE + 1);

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .containsExactly("groups");
  }
}
