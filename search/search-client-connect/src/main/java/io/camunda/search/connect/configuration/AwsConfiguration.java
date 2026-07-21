/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.configuration;

/**
 * AWS identity used when connecting to an AWS-hosted database. Supports static credentials or web
 * identity (IRSA); when no field is set, the AWS SDK default provider chain is used.
 */
public class AwsConfiguration {

  private String accessKey;
  private String secretKey;
  private String sessionToken;
  private String roleArn;
  private String webIdentityTokenFile;
  private String region;

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(final String accessKey) {
    this.accessKey = blankToNull(accessKey);
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(final String secretKey) {
    this.secretKey = blankToNull(secretKey);
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(final String sessionToken) {
    this.sessionToken = blankToNull(sessionToken);
  }

  public String getRoleArn() {
    return roleArn;
  }

  public void setRoleArn(final String roleArn) {
    this.roleArn = blankToNull(roleArn);
  }

  public String getWebIdentityTokenFile() {
    return webIdentityTokenFile;
  }

  public void setWebIdentityTokenFile(final String webIdentityTokenFile) {
    this.webIdentityTokenFile = blankToNull(webIdentityTokenFile);
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = blankToNull(region);
  }

  /**
   * Blank values (e.g. an env var that is set but empty) mean "unset"; null is the single
   * representation downstream.
   */
  private static String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
