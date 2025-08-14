/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.mockito.Mockito.verify;

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
    backupEndpointStandalone.take(11L);
    verify(backupEndpoint).take(11L);
  }

  @Test
  public void shouldCallListWhenIsStandalone() {
    backupEndpointStandalone.list();
    verify(backupEndpoint).list();
  }

  @Test
  public void shouldCallGetWhenIsStandalone() {
    backupEndpointStandalone.status(11L);
    verify(backupEndpoint).status(11L);
  }

  @Test
  public void shouldCallDeleteWhenIsStandalone() {
    backupEndpointStandalone.delete(11L);
    verify(backupEndpoint).delete(11L);
  }
}
