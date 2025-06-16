/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.cluster;

import java.io.File;
import java.util.Objects;

public class KeyStoreConfig {
  private File filePath;
  private String password;

  public File getFilePath() {
    return filePath;
  }

  public KeyStoreConfig setFilePath(final File filePath) {
    this.filePath = filePath;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public KeyStoreConfig setPassword(final String password) {
    this.password = password;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, password);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final KeyStoreConfig that = (KeyStoreConfig) o;
    return Objects.equals(filePath, that.filePath) && Objects.equals(password, that.password);
  }

  @Override
  public String toString() {
    final var passStr = password == null ? "" : "*****";
    return "KeyStoreCfg{" + "filePath=" + filePath + ", password='" + passStr + '\'' + '}';
  }
}
