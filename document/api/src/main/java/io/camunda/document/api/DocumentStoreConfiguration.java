/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record DocumentStoreConfiguration(
    String defaultDocumentStoreId,
    Integer threadPoolSize,
    List<DocumentStoreConfigurationRecord> documentStores) {

  public record DocumentStoreConfigurationRecord(
      String id,
      Class<? extends DocumentStoreProvider> providerClass,
      Map<String, String> properties) {

    /**
     * Substrings that mark a property key as carrying a credential. Such values must never reach a
     * log line or an exception message, so {@link #toString()} replaces them with {@link
     * #REDACTED}.
     *
     * <p>Matched as case-insensitive substrings rather than as an exact-name allowlist: store
     * properties also arrive through the legacy {@code DOCUMENT_STORE_<ID>_<PROPERTY>} environment
     * bridge, which forwards whatever property name it finds, so an allowlist would print in the
     * clear every credential key it had not been taught about.
     *
     * <p>{@code CREDENTIALS_PATH} is deliberately not covered — it names a key file rather than
     * holding a key, and it is the one value worth seeing when a store fails to read it.
     */
    private static final Set<String> SENSITIVE_KEY_MARKERS =
        Set.of("SECRET", "PASSWORD", "TOKEN", "KEY", "CONNECTION_STRING", "SIGNATURE");

    private static final String REDACTED = "<redacted>";

    /**
     * Renders the record with every credential-bearing property masked. The default record {@code
     * toString} would print the raw properties map, and this record is interpolated into
     * configuration error messages that end up in broker logs.
     */
    @Override
    public String toString() {
      return "DocumentStoreConfigurationRecord[id="
          + id
          + ", providerClass="
          + (providerClass == null ? null : providerClass.getName())
          + ", properties="
          + redactedProperties()
          + "]";
    }

    private Map<String, String> redactedProperties() {
      if (properties == null) {
        return null;
      }
      final Map<String, String> redacted = new LinkedHashMap<>();
      properties.forEach((key, value) -> redacted.put(key, isSensitive(key) ? REDACTED : value));
      return redacted;
    }

    private static boolean isSensitive(final String key) {
      if (key == null) {
        return false;
      }
      final String normalized = key.toUpperCase(Locale.ROOT);
      return SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }
  }
}
