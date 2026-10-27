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
 * Enforces two invariants on every MyBatis {@code resultMap} in {@code src/main/resources/mapper},
 * regardless of which package its target type lives in ({@code write.domain}, {@code
 * search.entities}, {@code read.domain}, {@code read.replication} -- every record-typed resultMap
 * target in each of these is covered today; see the {@code isRecord()} caveat below):
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
 * real constructor. Nested {@code <collection>} constructors are covered too: MyBatis registers
 * them as their own resultMap entries, so no separate tree-walking is needed to reach them.
 *
 * <p>Once every arg in a resultMap is named, the mapper XML's declaration order stops mattering
 * entirely: {@code ResultMap.Builder.build()} re-sorts named constructor args into the target's
 * actual declared parameter order before storing the resultMap, so a mapper XML with every arg
 * named and in a different order than the record's constructor is not itself a bug -- this is a
 * property of MyBatis's own resolution, not something this check (or a reviewer eyeballing column
 * order) needs to separately verify.
 *
 * <p><b>{@code isRecord()} is a proxy for the risk, not the risk itself.</b> The second invariant
 * only inspects statements whose resolved result type {@link Class#isRecord() is a record} --
 * MyBatis's positional-automapping fallback isn't actually record-specific, it fires for any class
 * resolved by constructor arity, so a hypothetical future plain (non-record) class mapped via
 * {@code resultType=} would silently evade this particular check. In practice this doesn't leave a
 * live gap for {@code write.domain}/{@code search.entities}, since {@code
 * RdbmsDbModelMustBeRecordArchTest}/{@code SearchEntityArchTest} independently enforce those two
 * packages stay records; {@code read.domain}/{@code read.replication} have no equivalent ArchUnit
 * guard today (though every type currently mapped there is already a record).
 */
class RdbmsMapperConstructorArgsMustBeNamedTest {

  private static final String MAPPER_DIR = "src/main/resources/mapper";

  @Test
  void everyMapperStatementMustUseANamedConstructorResultMap() throws Exception {
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
        if (!resultMap.getType().isRecord()) {
          // Scoped to records deliberately, not because non-records are immune (see the
          // isRecord() caveat in the class Javadoc) -- every current resultType= usage in this
          // directory targets a scalar (e.g. java.lang.Long for a COUNT(*)) or an
          // insert/update/delete's implicit void, neither of which has any constructor to bind
          // by name or position in the first place.
          continue;
        }
        softly
            .assertThat(resultMap.getId())
            .as(
                "%s must use an explicit resultMap= (not resultType=) for %s, since implicit"
                    + " automapping matches SELECT columns to constructor params by position, not"
                    + " by name",
                statementId, resultMap.getType().getSimpleName())
            .isNotEqualTo(statementId + "-Inline");
      }
    }
  }
}
