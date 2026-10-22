/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.store.InvalidStoreConfigException;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MaintenanceAppTest {

  private final Map<String, String> values = new HashMap<>();
  private final Settings settings = new Settings(values::get);

  @Test
  void shouldReadAFilesystemStore() {
    // given
    values.put("eventplane.store.type", "filesystem");
    values.put("eventplane.store.path", "/var/event-plane");

    // when
    final var config = MaintenanceApp.storeConfig(settings);

    // then
    assertThat(config).isEqualTo(StoreConfig.filesystem(Path.of("/var/event-plane")));
  }

  @Test
  void shouldReadAnS3Store() {
    // given
    values.put("eventplane.store.type", "S3");
    values.put("eventplane.store.bucket", "my-bucket");
    values.put("eventplane.store.basePath", "event-plane/");
    values.put("eventplane.store.region", "eu-west-1");
    values.put("eventplane.store.endpoint", "http://localhost:9000");
    values.put("eventplane.store.accessKeyId", "key");
    values.put("eventplane.store.secretAccessKey", "secret");
    values.put("eventplane.store.forcePathStyle", "true");

    // when
    final var config = MaintenanceApp.storeConfig(settings);

    // then
    assertThat(config.type()).isEqualTo(StoreConfig.Type.S3);
    assertThat(config.bucket()).isEqualTo("my-bucket");
    assertThat(config.basePath()).isEqualTo("event-plane/");
    assertThat(config.region()).isEqualTo("eu-west-1");
    assertThat(config.endpoint()).isEqualTo(URI.create("http://localhost:9000"));
    assertThat(config.accessKeyId()).isEqualTo("key");
    assertThat(config.secretAccessKey()).isEqualTo("secret");
    assertThat(config.forcePathStyle()).isTrue();
  }

  @Test
  void shouldNameTheSettingThatIsMissingAndItsEnvironmentVariable() {
    assertThatThrownBy(() -> MaintenanceApp.storeConfig(settings))
        .isInstanceOfSatisfying(
            InvalidMaintenanceConfigException.class,
            e -> assertThat(e.setting()).isEqualTo("eventplane.store.type"))
        .hasMessage(
            "Expected eventplane.store.type to be set, as a system property or as EVENTPLANE_STORE_TYPE, but got none");

    values.put("eventplane.store.type", "filesystem");
    assertThatThrownBy(() -> MaintenanceApp.storeConfig(settings))
        .isInstanceOfSatisfying(
            InvalidMaintenanceConfigException.class,
            e -> assertThat(e.setting()).isEqualTo("eventplane.store.path"));
  }

  @Test
  void shouldRejectAnUnknownStoreType() {
    values.put("eventplane.store.type", "gcs");

    assertThatThrownBy(() -> MaintenanceApp.storeConfig(settings))
        .isInstanceOf(InvalidStoreConfigException.class);
  }

  @Test
  void shouldTreatABlankValueAsUnset() {
    values.put("eventplane.store.type", "  ");

    assertThatThrownBy(() -> MaintenanceApp.storeConfig(settings))
        .isInstanceOf(InvalidMaintenanceConfigException.class);
  }

  @Test
  void shouldRejectAFlagThatIsNotABoolean() {
    values.put("eventplane.store.type", "s3");
    values.put("eventplane.store.bucket", "b");
    values.put("eventplane.store.forcePathStyle", "yes");

    assertThatThrownBy(() -> MaintenanceApp.storeConfig(settings))
        .hasMessage("Expected eventplane.store.forcePathStyle to be true or false, but got 'yes'");
  }

  @Test
  void shouldDefaultTheMaintenanceSettings() {
    // when
    final var config = MaintenanceApp.maintenanceConfig(settings);

    // then
    assertThat(config).isEqualTo(MaintenanceConfig.defaults());
    assertThat(MaintenanceApp.interval(settings)).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void shouldReadTheMaintenanceSettings() {
    // given
    values.put("eventplane.maintenance.rollupAge", "PT1M");
    values.put("eventplane.maintenance.rollupTargetBytes", "1048576");
    values.put("eventplane.maintenance.retention", "P30D");
    values.put("eventplane.maintenance.interval", "PT30S");

    // when
    final var config = MaintenanceApp.maintenanceConfig(settings);

    // then
    assertThat(config)
        .isEqualTo(new MaintenanceConfig(Duration.ofMinutes(1), 1 << 20, Duration.ofDays(30)));
    assertThat(MaintenanceApp.interval(settings)).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldRejectSettingsThatAreNotDurationsOrNumbers() {
    values.put("eventplane.maintenance.rollupAge", "a day");
    assertThatThrownBy(() -> MaintenanceApp.maintenanceConfig(settings))
        .hasMessage(
            "Expected eventplane.maintenance.rollupAge to be an ISO-8601 duration such as PT24H, but got 'a day'");

    values.put("eventplane.maintenance.rollupAge", "PT1H");
    values.put("eventplane.maintenance.rollupTargetBytes", "256MiB");
    assertThatThrownBy(() -> MaintenanceApp.maintenanceConfig(settings))
        .hasMessage(
            "Expected eventplane.maintenance.rollupTargetBytes to be a whole number, but got '256MiB'");

    values.put("eventplane.maintenance.interval", "PT0S");
    assertThatThrownBy(() -> MaintenanceApp.interval(settings))
        .hasMessage("Expected eventplane.maintenance.interval to be longer than 0, but got PT0S");
  }

  @Test
  void shouldNameTheEnvironmentVariableOfASetting() {
    assertThat(Settings.environmentName("eventplane.maintenance.rollupTargetBytes"))
        .isEqualTo("EVENTPLANE_MAINTENANCE_ROLLUPTARGETBYTES");
  }
}
