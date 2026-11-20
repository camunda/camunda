/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;

/**
 * Meters a {@link SecretStore} publishes about its own connection to the backend, as opposed to the
 * {@link SecretCacheMetricsDoc cache} in front of it and the engine's {@code
 * camunda.secret.resolution.*} meters behind it.
 *
 * <p>A store that signs in to its backend with a short-lived token (Azure Key Vault today, the
 * HashiCorp Vault store later) registers these once per instance and removes them when it is
 * closed. A store that does not sign in, such as the env or file store, emits none of them.
 *
 * <p>Like {@link SecretCacheMetricsDoc}, this implements Micrometer's own {@link
 * MeterDocumentation} rather than the repository's {@code ExtendedMeterDocumentation}, to keep
 * {@code zeebe-util} out of every store implementation.
 */
@SuppressWarnings("NullableProblems")
public enum SecretStoreMetricsDoc implements MeterDocumentation {
  /** Failed attempts of a secret store to sign in to its backend */
  SIGNIN_FAILURES {
    @Override
    public String getDescription() {
      return "Number of failed attempts of a secret store to obtain a sign-in token from its "
          + "backend's identity provider, per store. Every failed token request is counted, "
          + "including a background refresh that fails while the previous token is still valid: "
          + "that is the warning before resolutions start failing, so alert on any sustained "
          + "increase rather than waiting for camunda.secret.resolution.outcome to report the "
          + "store unavailable. The log names the sign-in method and the reason, once when "
          + "sign-in starts failing and once when it recovers.";
    }

    @Override
    public String getName() {
      return "camunda.secret.store.signin.failures";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {SecretStoreKeyNames.STORE, SecretStoreKeyNames.STORE_TYPE};
    }
  };

  /** Returns the description (also known as {@code help} in some systems) for the given meter. */
  public abstract String getDescription();

  @SuppressWarnings("NullableProblems")
  public enum SecretStoreKeyNames implements KeyName {
    /**
     * The ID of the store, as the operator configured it under {@code
     * camunda.secrets.stores.<type>.<id>}. The same key as {@link
     * SecretCacheMetricsDoc.SecretCacheKeyNames#STORE}, so a dashboard can join a store's sign-in
     * failures with its cache and resolution meters.
     *
     * <p>A store ID is only unique within one physical tenant: the Spring wiring hands each store a
     * registry tagged with its physical tenant ID.
     */
    STORE("store"),
    /**
     * The type of the store, for example {@code azure}: the {@code <type>} segment of {@code
     * camunda.secrets.stores.<type>.<id>}.
     */
    STORE_TYPE("storeType");

    private final String key;

    SecretStoreKeyNames(final String key) {
      this.key = key;
    }

    @Override
    public String asString() {
      return key;
    }
  }
}
