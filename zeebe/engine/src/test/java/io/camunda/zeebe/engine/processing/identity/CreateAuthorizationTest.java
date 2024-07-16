/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateAuthorizationTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAuthorization() {
    final var createdAuthorization =
        ENGINE
            .authorization()
            .newAuthorization("blah")
            .withResourceKey("foo")
            .withResourceType("process-definition")
            .withPermissions(List.of("*:read"))
            .create();

    System.out.println("foo");
  }

  @Test
  public void shouldNotCreateDuplicate() {
    ENGINE
        .authorization()
        .newAuthorization("blah")
        .withResourceKey("foo")
        .withResourceType("process-definition")
        .withPermissions(List.of("*:read"))
        .create();

    ENGINE
        .authorization()
        .newAuthorization("blah")
        .withResourceKey("foo")
        .withResourceType("process-definition")
        .withPermissions(List.of("*:create"))
        .create();

    System.out.println("foo");
    System.out.println("bar");
  }
}
