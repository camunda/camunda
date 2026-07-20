/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This section configures the AWS identity used by all AWS consumers of a tenant (e.g. OpenSearch).
 *
 * <p>Canonical unified configuration properties are under {@code camunda.aws.*}:
 *
 * <ul>
 *   <li>{@code camunda.aws.access-key}, {@code camunda.aws.secret-key}, {@code
 *       camunda.aws.session-token} — static credentials
 *   <li>{@code camunda.aws.role-arn}, {@code camunda.aws.web-identity-token-file} — web identity
 *       (IRSA)
 *   <li>{@code camunda.aws.region}
 * </ul>
 *
 * <p>When no field is set, the AWS SDK default credentials provider chain is used, preserving the
 * previous environment-based behavior. AWS configuration is overridable per physical tenant via
 * {@code camunda.physical-tenants.<id>.aws.*}; a tenant that declares no {@code aws} block inherits
 * the root block.
 */
@NullMarked
public class Aws {

  /** Access key id of static AWS credentials. Must be set together with {@code secret-key}. */
  private @Nullable String accessKey;

  /** Secret access key of static AWS credentials. Must be set together with {@code access-key}. */
  private @Nullable String secretKey;

  /** Optional session token accompanying static AWS credentials. */
  private @Nullable String sessionToken;

  /**
   * ARN of the IAM role to assume via web identity (IRSA). Must be set together with {@code
   * web-identity-token-file}.
   */
  private @Nullable String roleArn;

  /**
   * Path to the web identity token file used to assume {@code role-arn}. Must be set together with
   * {@code role-arn}.
   */
  private @Nullable String webIdentityTokenFile;

  /** AWS region. When unset, the AWS SDK default region provider chain is used. */
  private @Nullable String region;

  public boolean hasStaticCredentials() {
    return accessKey != null && secretKey != null;
  }

  public boolean hasWebIdentity() {
    return roleArn != null && webIdentityTokenFile != null;
  }

  public @Nullable String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(final @Nullable String accessKey) {
    this.accessKey = accessKey;
  }

  public @Nullable String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(final @Nullable String secretKey) {
    this.secretKey = secretKey;
  }

  public @Nullable String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(final @Nullable String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public @Nullable String getRoleArn() {
    return roleArn;
  }

  public void setRoleArn(final @Nullable String roleArn) {
    this.roleArn = roleArn;
  }

  public @Nullable String getWebIdentityTokenFile() {
    return webIdentityTokenFile;
  }

  public void setWebIdentityTokenFile(final @Nullable String webIdentityTokenFile) {
    this.webIdentityTokenFile = webIdentityTokenFile;
  }

  public @Nullable String getRegion() {
    return region;
  }

  public void setRegion(final @Nullable String region) {
    this.region = region;
  }
}
