/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.util.Environment;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public class TestConfigurationFactoryTest {

  private final TestConfigurationFactory sutConfigurationFactory = new TestConfigurationFactory();

  @Test
  public void shouldReadConfiguration() {
    // when
    final SampleConfiguration actual =
        sutConfigurationFactory.create(
            null, "config", "TestConfigurationFactoryTestSample.yaml", SampleConfiguration.class);

    // then
    assertThat(actual.getSetting()).isEqualTo("test");
    assertThat(actual.getArgs()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldReadEmptyConfiguration()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
          IllegalAccessException {
    // when
    final SampleConfiguration actual =
        sutConfigurationFactory.create(
            null, "config", "TestConfigurationFactoryTestEmpty.yaml", SampleConfiguration.class);

    // then
    assertThat(actual.getSetting()).isEqualTo(null);
    assertThat(actual.getArgs()).isNull();
  }

  @Test
  public void shouldOverlayEnvironmentSettingsOverConfigurationReadFromFile()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
          IllegalAccessException {
    // given
    final Map<String, String> environmentEntries = new HashMap<>();
    environmentEntries.put("config.args.foo", "not bar");
    final Environment environment = new Environment(environmentEntries);

    // when
    final SampleConfiguration actual =
        sutConfigurationFactory.create(
            environment,
            "config",
            "TestConfigurationFactoryTestSample.yaml",
            SampleConfiguration.class);

    // then
    assertThat(actual.getArgs()).containsOnly(entry("foo", "not bar"));
    assertThat(actual.getSize()).isEqualTo(DataSize.ofMegabytes(2));
  }

  @Test
  public void shouldOverlayEnvironmentSettingsWithLenientNamingOverConfigurationReadFromFile()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException,
          IllegalAccessException {
    // given
    final Map<String, String> environmentEntries = new HashMap<>();
    environmentEntries.put("CONFIG_ARGS_FOO", "not bar");
    final Environment environment = new Environment(environmentEntries);

    // when
    final SampleConfiguration actual =
        sutConfigurationFactory.create(
            environment,
            "config",
            "TestConfigurationFactoryTestSample.yaml",
            SampleConfiguration.class);

    // then
    assertThat(actual.getArgs()).containsOnly(entry("foo", "not bar"));
  }

  public static final class SampleConfiguration {
    private String setting;
    private Map<String, Object> args;
    private DataSize size;

    public String getSetting() {
      return setting;
    }

    public void setSetting(final String setting) {
      this.setting = setting;
    }

    public Map<String, Object> getArgs() {
      return args;
    }

    public void setArgs(final Map<String, Object> args) {
      this.args = args;
    }

    public DataSize getSize() {
      return size;
    }

    public void setSize(final DataSize size) {
      this.size = size;
    }
  }
}
