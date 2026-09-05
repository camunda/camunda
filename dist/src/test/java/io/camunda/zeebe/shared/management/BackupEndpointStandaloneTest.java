/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.mockito.Mockito.verify;

import io.camunda.zeebe.backup.client.api.BackupApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {BackupEndpointStandalone.class})
public abstract class BackupEndpointStandaloneTest {

  @MockitoBean private BackupEndpoint backupEndpoint;
  @Autowired private BackupEndpointStandalone backupEndpointStandalone;

  @Test
  public void shouldCallTakeWhenIsStandalone() {
    // when
    backupEndpointStandalone.take(11L, null);

    // then
    verify(backupEndpoint).take(11L, null);
  }

  @Test
  public void shouldCallTakeWithoutIdWhenIsStandalone() {
    // when
    backupEndpointStandalone.take();

    // then
    verify(backupEndpoint).take();
  }

  @Test
  public void shouldCallListWhenIsStandalone() {
    // when
    backupEndpointStandalone.query(null, null);

    // then
    verify(backupEndpoint).query(null, null);
  }

  @Test
  public void shouldCallGetWhenIsStandalone() {
    // when
    backupEndpointStandalone.query("11", null);

    // then
    verify(backupEndpoint).query("11", null);
  }

  @Test
  public void shouldCallDeleteWhenIsStandalone() {
    // when
    backupEndpointStandalone.delete("11", null);

    // then
    verify(backupEndpoint).delete("11", null);
  }

  @Test
  public void shouldCallQueryWhenIsStandalone() {
    // when
    backupEndpointStandalone.query(BackupApi.STATE, null);

    // then
    verify(backupEndpoint).query(BackupApi.STATE, null);
  }
}
