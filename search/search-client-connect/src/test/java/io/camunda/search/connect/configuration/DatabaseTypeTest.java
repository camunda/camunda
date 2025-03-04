/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.se.config.DatabaseConfig;
import io.camunda.db.se.config.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class DatabaseTypeTest {
  @Test
  public void stringValuesShouldMatchEnum() {
    assertThat(DatabaseConfig.ELASTICSEARCH).isEqualTo(DatabaseType.ELASTICSEARCH.toString());
    assertThat(DatabaseConfig.OPENSEARCH).isEqualTo(DatabaseType.OPENSEARCH.toString());
    assertThat(DatabaseConfig.RDBMS).isEqualTo(DatabaseType.RDBMS.toString());
  }

  @EnumSource(DatabaseType.class)
  @ParameterizedTest
  public void shouldBeCreatedCaseInsensitive(final DatabaseType databaseType) {
    assertThat(DatabaseType.from(databaseType.name().toLowerCase())).isEqualTo(databaseType);
  }

  @EnumSource(DatabaseType.class)
  @ParameterizedTest
  public void shouldReturnCorrectIsElasticSearch(final DatabaseType databaseType) {
    assertThat(databaseType.isElasticSearch())
        .isEqualTo(databaseType.toString().equals("elasticsearch"));
  }

  @EnumSource(DatabaseType.class)
  @ParameterizedTest
  public void shouldReturnCorrectIsOpensearch(final DatabaseType databaseType) {
    assertThat(databaseType.isOpenSearch()).isEqualTo(databaseType.toString().equals("opensearch"));
  }

  @EnumSource(DatabaseType.class)
  @ParameterizedTest
  public void shouldReturnCorrectIsRdbms(final DatabaseType databaseType) {
    assertThat(databaseType.isRdbms()).isEqualTo(databaseType.toString().equals("rdbms"));
  }
}
