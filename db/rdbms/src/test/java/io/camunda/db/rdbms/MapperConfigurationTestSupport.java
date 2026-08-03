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

/**
 * Shared support for tests that enforce constructor-mapping invariants on the mapper XMLs in {@code
 * src/main/resources/mapper}, parsed via MyBatis's own {@link XMLMapperBuilder} into a real {@link
 * Configuration} -- the same way production does -- so the checks below reuse MyBatis's own
 * constructor-arg resolution instead of re-deriving it.
 *
 * <p>Used by {@link RdbmsDbModelConstructorArgsMustBeNamedTest} (scoped to {@code
 * io.camunda.db.rdbms.write.domain}) and {@link SearchEntityConstructorArgsMustBeNamedTest} (scoped
 * to {@code io.camunda.search.entities}).
 */
final class MapperConfigurationTestSupport {

  private static final String MAPPER_DIR = "src/main/resources/mapper";

  private MapperConfigurationTestSupport() {}

  /** Parses every mapper XML into one shared {@link Configuration}, mirroring production. */
  static Configuration parseAllMapperFiles() throws Exception {
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
    return configuration;
  }

  /**
   * Asserts every constructor-flagged {@code ResultMapping} whose resultMap target type starts with
   * {@code packagePrefix} has a non-blank name (from the mapper XML's {@code name=} attribute). A
   * resultMap with no {@code name=} on any arg is tolerated silently by MyBatis via positional
   * fallback matching -- this is the one case that check needs to catch, since a name= that doesn't
   * match a real constructor parameter, or a javaType that doesn't match that parameter's declared
   * type, already fails loudly as a {@code BuilderException} while parsing, before this assertion
   * ever runs. Once every arg in a resultMap is named, the mapper XML's declaration order stops
   * mattering entirely: {@code ResultMap.Builder.build()} re-sorts named constructor args into the
   * target's actual declared parameter order before storing the resultMap, so a mapper XML with
   * every arg named and in a different order than the record's constructor is not itself a bug --
   * this is a property of MyBatis's own resolution, not something this check (or a reviewer
   * eyeballing column order) needs to separately verify.
   */
  static void assertConstructorArgsNamed(
      final Configuration configuration, final String packagePrefix, final SoftAssertions softly) {
    for (final String resultMapId : configuration.getResultMapNames()) {
      if (!resultMapId.contains(".")) {
        // Short-id alias for the same resultMap, registered under its unqualified id -- skip to
        // avoid checking every resultMap twice.
        continue;
      }
      final var resultMap = configuration.getResultMap(resultMapId);
      if (!resultMap.getType().getName().startsWith(packagePrefix)) {
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

  /**
   * Asserts no statement whose resolved result type starts with {@code packagePrefix} relies on
   * implicit {@code resultType=} automapping. MyBatis never registers a {@code resultType=}-only
   * mapping in the shared {@link Configuration} -- it builds an anonymous {@code
   * "<statementId>-Inline"} {@code ResultMap} attached only to that statement. Its presence here
   * means the statement has no explicit {@code resultMap=}/{@code <constructor>} and is relying on
   * implicit automapping instead, which for a record matches SELECT columns to constructor
   * parameters by JDBC result-set *position*, not by column label (verified directly: a
   * scrambled-column {@code resultType=} select silently fed a {@code String} column into a {@code
   * long} constructor parameter and blew up on the type coercion) -- the exact column-order risk
   * this whole invariant series exists to eliminate, just without an XML block to even name.
   */
  static void assertNoImplicitResultType(
      final Configuration configuration, final String packagePrefix, final SoftAssertions softly) {
    for (final String statementId : configuration.getMappedStatementNames()) {
      if (!statementId.contains(".")) {
        // Short-id alias for the same statement, registered under its unqualified id -- skip to
        // avoid checking every statement twice.
        continue;
      }
      final var statement = configuration.getMappedStatement(statementId);
      for (final var resultMap : statement.getResultMaps()) {
        if (!resultMap.getType().getName().startsWith(packagePrefix)) {
          continue;
        }
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
