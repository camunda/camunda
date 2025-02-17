/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class LiquibaseScriptGeneratorTest {

  @Test
  public void testGenerateSqlScript() throws Exception {
    // given

    // when
    final String sqlScript = LiquibaseScriptGenerator.generateSqlScript("h2", "test.xml", "");

    // then
    final var expected = Files.readString(Paths.get("src/test/resources/test.h2.sql"));
    assertThat(sqlScript).isEqualTo(expected);
  }

  @Test
  public void testGenerateSqlScriptWithPrefix() throws Exception {
    // given

    // when
    final String sqlScript = LiquibaseScriptGenerator.generateSqlScript("h2", "test.xml", "C8_");

    // then
    final var expected = Files.readString(Paths.get("src/test/resources/test_prefix.h2.sql"));
    assertThat(sqlScript).isEqualTo(expected);
  }
}
