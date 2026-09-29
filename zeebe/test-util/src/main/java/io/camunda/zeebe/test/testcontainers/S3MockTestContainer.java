/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;

public final class S3MockTestContainer extends S3MockContainer {

  private static final String DEFAULT_TAG = "4.11.0";
  private static final String DEFAULT_REGION = "us-east-1";
  private static final String DEFAULT_ACCESS_KEY = "accessKey";
  private static final String DEFAULT_SECRET_KEY = "secretKey";
  private static final int INTERNAL_HTTP_PORT = 9090;

  public S3MockTestContainer() {
    this(DEFAULT_TAG);
  }

  public S3MockTestContainer(final String tag) {
    super(tag);
  }

  public String externalEndpoint() {
    return getHttpEndpoint();
  }

  public String internalEndpoint(final String networkAlias) {
    return "http://%s:%d".formatted(networkAlias, INTERNAL_HTTP_PORT);
  }

  public String region() {
    return DEFAULT_REGION;
  }

  public String accessKey() {
    return DEFAULT_ACCESS_KEY;
  }

  public String secretKey() {
    return DEFAULT_SECRET_KEY;
  }
}
