/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.restore.RestoreApp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Represents an instance of the {@link RestoreApp} Spring application. */
public final class TestRestoreApp extends TestSpringApplication<TestRestoreApp> {
  private final Camunda config;
  private long[] backupId;
  private Instant from;
  private Instant to;

  public TestRestoreApp() {
    this(new Camunda());
  }

  public TestRestoreApp(final Camunda config) {
    super(RestoreApp.class);
    this.config = config;

    //noinspection resource
    withBean("config", config, Camunda.class).withAdditionalProfile(Profile.RESTORE);
  }

  @Override
  public TestRestoreApp self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(config.getCluster().getNodeId()));
  }

  @Override
  public HealthActuator healthActuator() {
    return new HealthActuator.NoopHealthActuator();
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  @Override
  public TestRestoreApp withUnifiedConfig(final Consumer<Camunda> modifier) {
    modifier.accept(config);
    return this;
  }

  @Override
  protected String[] commandLineArgs() {
    final List<String> args = new ArrayList<>(List.of(super.commandLineArgs()));
    if (backupId != null) {
      args.add(
          "--backupId="
              + Arrays.stream(backupId).mapToObj(Long::toString).collect(Collectors.joining(",")));
    } else if (from != null && to != null) {
      args.add("--from=" + from);
      args.add("--to=" + to);
    }
    return args.toArray(String[]::new);
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }

  public TestRestoreApp withBackupId(final long... backupId) {
    this.backupId = backupId;
    return this;
  }

  public TestRestoreApp withTimeRange(final Instant from, final Instant to) {
    this.from = from;
    this.to = to;
    return this;
  }
}
