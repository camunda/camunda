/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Read-side counterpart to {@link RdbmsDbModelConstructorArgsMustBeNamedTest}: enforces the same
 * two invariants, scoped to {@code io.camunda.search.entities.*Entity} instead of {@code
 * io.camunda.db.rdbms.write.domain.*DbModel}.
 *
 * <p>Two entities in this package (e.g. {@code ClusterVariableEntity}, {@code
 * ProcessInstanceEntity}) are deliberately mapped via a non-canonical secondary constructor of a
 * different arity than the canonical one, so a sibling {@code <collection>} can hydrate a mutable
 * field (a list/set) separately. This does not affect either check here: a non-blank {@code name=}
 * and the absence of an implicit {@code resultType=} are both agnostic to *which* constructor
 * MyBatis resolves against -- they only require that one was resolved by name at all.
 *
 * <p>{@code io.camunda.search.entities} records are also constructed via Jackson (Elasticsearch/
 * OpenSearch document deserialization) elsewhere in the codebase; that path is unaffected by these
 * checks, which are scoped entirely to this module's MyBatis mapper XMLs.
 */
class SearchEntityConstructorArgsMustBeNamedTest {

  private static final String SEARCH_ENTITY_PACKAGE_PREFIX = "io.camunda.search.entities.";

  @Test
  void everySearchEntityStatementMustUseANamedConstructorResultMap() throws Exception {
    final var configuration = MapperConfigurationTestSupport.parseAllMapperFiles();

    final var softly = new SoftAssertions();
    MapperConfigurationTestSupport.assertConstructorArgsNamed(
        configuration, SEARCH_ENTITY_PACKAGE_PREFIX, softly);
    MapperConfigurationTestSupport.assertNoImplicitResultType(
        configuration, SEARCH_ENTITY_PACKAGE_PREFIX, softly);
    softly.assertAll();
  }
}
