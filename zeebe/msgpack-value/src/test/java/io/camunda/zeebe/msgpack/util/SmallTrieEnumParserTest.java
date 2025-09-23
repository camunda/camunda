/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.BeforeEach;

class SmallTrieEnumParserTest extends AbstractEnumParserPropertyTest {
  @Override
  @BeforeEach
  @BeforeProperty
  void setUp() {
    parser = new TrieEnumParser<>(TestEnum.class);
    singleParser = new TrieEnumParser<>(SingleEnum.class);
    minimalParser = new TrieEnumParser<>(EmptyLikeEnum.class);
  }
}
