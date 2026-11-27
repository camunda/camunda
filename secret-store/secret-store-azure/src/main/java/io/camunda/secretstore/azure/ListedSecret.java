/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * One secret as Key Vault lists it: the reference name, without the path prefix, and the properties
 * that decide its {@link SecretStatus}, kept as Key Vault returned them so the status can be
 * derived later with the same clock and rules as resolution, without another call. Never a value:
 * listing does not read one.
 *
 * @param name the reference name, the Key Vault secret name without the path prefix
 * @param enabled whether the secret is enabled; {@code null} when Key Vault did not say
 * @param expiresOn when the secret expires, or {@code null} for never
 * @param notBefore when the secret becomes active, or {@code null} for always
 */
record ListedSecret(
    String name,
    @Nullable Boolean enabled,
    @Nullable OffsetDateTime expiresOn,
    @Nullable OffsetDateTime notBefore) {}
