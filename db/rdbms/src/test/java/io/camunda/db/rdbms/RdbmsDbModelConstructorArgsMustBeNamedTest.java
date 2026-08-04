/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
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
 * Configuration}, the same way production does (see {@link MapperConfigurationTestSupport}). This
 * also means a {@code name=} that does not match any real constructor parameter, or a {@code
 * javaType} that does not match the matching parameter's declared type, already fails loudly here
 * as a {@code BuilderException} while parsing -- MyBatis itself resolves constructor args by exact
 * name-and-type match against the target's real constructor, for every one of these classes' single
 * canonical (record) constructor. Nested {@code <collection>} constructors are covered too: MyBatis
 * registers them as their own resultMap entries, so no separate tree-walking is needed to reach
 * them.
 *
 * <p>Read-side {@code *Entity} resultMaps are intentionally out of scope -- see {@link
 * SearchEntityConstructorArgsMustBeNamedTest} for those.
 */
class RdbmsDbModelConstructorArgsMustBeNamedTest {

  private static final String DB_MODEL_PACKAGE_PREFIX = "io.camunda.db.rdbms.write.domain.";

  @Test
  void everyDbModelStatementMustUseANamedConstructorResultMap() throws Exception {
    final var configuration = MapperConfigurationTestSupport.parseAllMapperFiles();

    final var softly = new SoftAssertions();
    MapperConfigurationTestSupport.assertConstructorArgsNamed(
        configuration, DB_MODEL_PACKAGE_PREFIX, softly);
    MapperConfigurationTestSupport.assertNoImplicitResultType(
        configuration, DB_MODEL_PACKAGE_PREFIX, softly);
    softly.assertAll();
  }
}
