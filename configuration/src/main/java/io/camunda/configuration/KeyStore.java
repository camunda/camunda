/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.io.File;

public class KeyStore {

  /** The path for keystore file */
  private File filePath;

  /** Sets the password for the keystore file, if not set it is assumed there is no password */
  private String password;

  public File getFilePath() {
    return filePath;
  }

  public void setFilePath(final File filePath) {
    this.filePath = filePath;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public KeyStore clone() {
    final KeyStore copy = new KeyStore();
    copy.filePath = filePath;
    copy.password = password;
    return copy;
  }
}
