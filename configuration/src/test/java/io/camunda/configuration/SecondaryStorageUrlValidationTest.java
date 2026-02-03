/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for validating that 'url' and 'urls' configuration properties are mutually exclusive in
 * document-based secondary storage databases.
 */
public class SecondaryStorageUrlValidationTest {

  /** Test implementation of DocumentBasedSecondaryStorageDatabase for unit testing. */
  private static final class TestDocumentBasedDatabase
      extends DocumentBasedSecondaryStorageDatabase {

    @Override
    public String databaseName() {
      return "TestDatabase";
    }
  }

  @Nested
  class WhenBothUrlAndUrlsAreConfigured {

    @Test
    void shouldThrowExceptionWhenAccessingUrl() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrl("http://custom-url:9200");
      database.setUrls(List.of("http://node1:9200", "http://node2:9200"));

      // when/then
      assertThatThrownBy(database::getUrl)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot configure both 'url' and 'urls'")
          .hasMessageContaining("TestDatabase");
    }

    @Test
    void shouldThrowExceptionWhenAccessingUrls() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrl("http://custom-url:9200");
      database.setUrls(List.of("http://node1:9200", "http://node2:9200"));

      // when/then
      assertThatThrownBy(database::getUrls)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot configure both 'url' and 'urls'")
          .hasMessageContaining("TestDatabase");
    }
  }

  @Nested
  class WhenOnlyUrlIsConfigured {

    @Test
    void shouldAllowAccessingUrl() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrl("http://custom-url:9200");

      // when/then
      assertThatNoException().isThrownBy(database::getUrl);
      assertThat(database.getUrl()).isEqualTo("http://custom-url:9200");
    }

    @Test
    void shouldAllowAccessingUrls() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrl("http://custom-url:9200");

      // when/then
      assertThatNoException().isThrownBy(database::getUrls);
      assertThat(database.getUrls()).isEmpty();
    }
  }

  @Nested
  class WhenOnlyUrlsIsConfigured {

    @Test
    void shouldAllowAccessingUrl() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrls(List.of("http://node1:9200", "http://node2:9200"));

      // when/then - url is default, so no conflict
      assertThatNoException().isThrownBy(database::getUrl);
    }

    @Test
    void shouldAllowAccessingUrls() {
      // given
      final var database = new TestDocumentBasedDatabase();
      database.setUrls(List.of("http://node1:9200", "http://node2:9200"));

      // when/then
      assertThatNoException().isThrownBy(database::getUrls);
      assertThat(database.getUrls()).containsExactly("http://node1:9200", "http://node2:9200");
    }
  }
}
