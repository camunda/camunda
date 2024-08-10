/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.webapp.security.Permission;
import org.junit.jupiter.api.Test;

public class PermissionsConverterTest {

  private final PermissionConverter underTest = new PermissionConverter();

  @Test
  public void testConvertReadPermission() {
    assertThat(underTest.convert(PermissionConverter.READ_PERMISSION_VALUE))
        .isEqualTo(Permission.READ);
  }

  @Test
  public void testConvertWritePermission() {
    assertThat(underTest.convert(PermissionConverter.WRITE_PERMISSION_VALUE))
        .isEqualTo(Permission.WRITE);
  }

  @Test
  public void testConvertInvalidPermission() {
    assertThat(underTest.convert("delete")).isNull();
  }
}
