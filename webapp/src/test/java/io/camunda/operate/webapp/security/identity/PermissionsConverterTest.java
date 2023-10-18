/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.webapp.security.Permission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PermissionsConverterTest {

  private final PermissionConverter underTest = new PermissionConverter();

  @Test
  public void testConvertReadPermission() {
    assertThat(underTest.convert(PermissionConverter.READ_PERMISSION_VALUE)).isEqualTo(Permission.READ);
  }

  @Test
  public void testConvertWritePermission() {
    assertThat(underTest.convert(PermissionConverter.WRITE_PERMISSION_VALUE)).isEqualTo(Permission.WRITE);
  }

  @Test
  public void testConvertInvalidPermission() {
    assertThat(underTest.convert("delete")).isNull();
  }
}
