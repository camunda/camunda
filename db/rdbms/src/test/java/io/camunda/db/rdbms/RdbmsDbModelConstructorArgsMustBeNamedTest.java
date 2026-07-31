/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.session.Configuration;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Enforces two invariants on every MyBatis statement whose result type is a {@code
 * io.camunda.db.rdbms.write.domain.*DbModel}:
 *
 * <ul>
 *   <li>every constructor arg carries a non-blank {@code name=} attribute, so MyBatis's
 *       constructor-arg binding is order-independent instead of relying on column declaration order
 *       matching Java parameter order
 *   <li>the statement uses an explicit {@code resultMap=}, not a bare {@code resultType=} -- {@code
 *       resultType=} on a record with no matching {@code <resultMap>} falls back to MyBatis's
 *       implicit automapping, which for records matches SELECT columns to constructor parameters by
 *       JDBC result-set *position*, not by column label (verified directly: a scrambled-column
 *       {@code resultType=} select silently fed a {@code String} column into a {@code long}
 *       constructor parameter and blew up on the type coercion) -- the exact column-order risk this
 *       whole invariant series exists to eliminate, just without an XML block to even name
 * </ul>
 *
 * Mapper XMLs are parsed with MyBatis's own {@link XMLMapperBuilder} into a real {@link
 * Configuration}, the same way production does. This also means a {@code name=} that does not match
 * any real constructor parameter, or a {@code javaType} that does not match the matching
 * parameter's declared type, already fails loudly here as a {@code BuilderException} while parsing
 * -- MyBatis itself resolves constructor args by exact name-and-type match against the target's
 * real constructor, for every one of these classes' single canonical (record) constructor. Nested
 * {@code <collection>} constructors are covered too: MyBatis registers them as their own resultMap
 * entries, so no separate tree-walking is needed to reach them.
 *
 * <p>Read-side {@code *Entity} resultMaps are intentionally out of scope -- only {@code
 * write.domain} DbModels are covered by this series' invariant enforcement.
 */
class RdbmsDbModelConstructorArgsMustBeNamedTest {

  private static final String MAPPER_DIR = "src/main/resources/mapper";
  private static final String DB_MODEL_PACKAGE_PREFIX = "io.camunda.db.rdbms.write.domain.";

  @Test
  void everyDbModelStatementMustUseANamedConstructorResultMap() throws Exception {
    final var mapperDir = new File(MAPPER_DIR);
    assertThat(mapperDir).as("mapper resources directory").isDirectory();
    final var mapperFiles = mapperDir.listFiles((dir, name) -> name.endsWith(".xml"));
    assertThat(mapperFiles).as("mapper XML files").isNotEmpty();

    final var configuration = new Configuration();
    for (final File mapperFile : mapperFiles) {
      try (var inputStream = new FileInputStream(mapperFile)) {
        new XMLMapperBuilder(
                inputStream, configuration, mapperFile.getPath(), configuration.getSqlFragments())
            .parse();
      }
    }

    final var softly = new SoftAssertions();
    checkEveryConstructorArgIsNamed(configuration, softly);
    checkNoStatementUsesImplicitResultType(configuration, softly);
    softly.assertAll();
  }

  private void checkEveryConstructorArgIsNamed(
      final Configuration configuration, final SoftAssertions softly) {
    for (final String resultMapId : configuration.getResultMapNames()) {
      if (!resultMapId.contains(".")) {
        // Short-id alias for the same resultMap, registered under its unqualified id -- skip to
        // avoid checking every resultMap twice.
        continue;
      }
      final var resultMap = configuration.getResultMap(resultMapId);
      if (!resultMap.getType().getName().startsWith(DB_MODEL_PACKAGE_PREFIX)) {
        continue;
      }
      for (final var mapping : resultMap.getResultMappings()) {
        if (mapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          softly
              .assertThat(mapping.getProperty())
              .as(
                  "%s <constructor arg column=\"%s\"> must have a non-blank name=",
                  resultMapId, mapping.getColumn())
              .isNotBlank();
        }
      }
    }
  }

  private void checkNoStatementUsesImplicitResultType(
      final Configuration configuration, final SoftAssertions softly) {
    for (final String statementId : configuration.getMappedStatementNames()) {
      if (!statementId.contains(".")) {
        // Short-id alias for the same statement, registered under its unqualified id -- skip to
        // avoid checking every statement twice.
        continue;
      }
      final var statement = configuration.getMappedStatement(statementId);
      for (final var resultMap : statement.getResultMaps()) {
        if (!resultMap.getType().getName().startsWith(DB_MODEL_PACKAGE_PREFIX)) {
          continue;
        }
        // MyBatis never registers a resultType=-only mapping in the shared Configuration -- it
        // builds an anonymous "<statementId>-Inline" ResultMap attached only to this statement.
        // Its presence here means the statement has no explicit <resultMap>/<constructor> and is
        // relying on implicit automapping instead.
        softly
            .assertThat(resultMap.getId())
            .as(
                "%s must use an explicit resultMap= (not resultType=) for %s, since implicit"
                    + " automapping matches SELECT columns to constructor params by position, not"
                    + " by name",
                statementId, resultMap.getType().getSimpleName())
            .doesNotEndWith("-Inline");
      }
    }
  }
}
