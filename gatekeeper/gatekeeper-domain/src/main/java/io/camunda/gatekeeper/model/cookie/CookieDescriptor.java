/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.cookie;

import java.time.Duration;

/** Cookie configuration value object. */
public record CookieDescriptor(
    String namePrefix,
    String path,
    boolean secure,
    boolean httpOnly,
    SameSitePolicy sameSite,
    int maxSizeBytes,
    Duration maxAge) {}
