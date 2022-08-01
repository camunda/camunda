/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ThreadsCfgTest {
  @Test
  void shouldChooseDefaultCpuThreadCount() {
    // given
    final ThreadsCfg cfg = new ThreadsCfg();

    // when
    final int cpuThreadCount = cfg.getCpuThreadCount();

    // then
    assertThat(cpuThreadCount).isEqualTo(2);
  }

  @Test
  void shouldChooseDefaultIoThreadCount() {
    // given
    final ThreadsCfg cfg = new ThreadsCfg();

    // when
    final int ioThreadCount = cfg.getIoThreadCount();

    // then
    assertThat(ioThreadCount).isEqualTo(2);
  }

  @Test
  void shouldSetCpuThreadCount() {
    // given
    final ThreadsCfg cfg = new ThreadsCfg();
    cfg.setCpuThreadCount(3);

    // when
    final int cpuThreadCount = cfg.getCpuThreadCount();

    // then
    assertThat(cpuThreadCount).isEqualTo(3);
  }

  @Test
  void shouldSetCpuThreadCountFromConfig() {
    // given
    final var cfg = TestConfigReader.readConfig("threads-cfg", Collections.emptyMap()).getThreads();

    // when
    final int cpuThreadCount = cfg.getCpuThreadCount();

    // then
    assertThat(cpuThreadCount).isEqualTo(5);
  }

  @Test
  void shouldSetCpuThreadCountFromEnvironment() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.threads.cpuThreadCount", "6");
    final var cfg = TestConfigReader.readConfig("threads-cfg", environment).getThreads();

    // when
    final var cpuThreadCount = cfg.getCpuThreadCount();

    // then
    assertThat(cpuThreadCount).isEqualTo(6);
  }

  @Test
  void shouldSetIoThreadCount() {
    // given
    final ThreadsCfg cfg = new ThreadsCfg();
    cfg.setIoThreadCount(3);

    // when
    final int ioThreadCount = cfg.getIoThreadCount();

    // then
    assertThat(ioThreadCount).isEqualTo(3);
  }

  @Test
  void shouldSetIoThreadCountFromConfig() {
    // given
    final var cfg = TestConfigReader.readConfig("threads-cfg", Collections.emptyMap()).getThreads();

    // when
    final int ioThreadCount = cfg.getIoThreadCount();

    // then
    assertThat(ioThreadCount).isEqualTo(7);
  }

  @Test
  void shouldSetIoThreadCountFromEnv() {
    // given
    final var environment = Collections.singletonMap("zeebe.broker.threads.ioThreadCount", "6");
    final var cfg = TestConfigReader.readConfig("threads-cfg", environment).getThreads();

    // when
    final int cpuThreadCount = cfg.getIoThreadCount();

    // then
    assertThat(cpuThreadCount).isEqualTo(6);
  }
}
