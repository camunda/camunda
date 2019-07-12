/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import sun.misc.VM;

public class VerifyDirectMemoryTest {
  @Test
  public void shouldEnsureDirectMemory() {
    assertThat(VM.maxDirectMemory()).isGreaterThanOrEqualTo(4 * 1024 * 1024 * 1024); // 4 gigabyte
  }
}
