/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig;

import io.zeebe.legacy.tomlconfig.util.Loggers;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class LegacyConfigurationSupport {
  private static final Map<String, Replacement> MAPPING_GATEWAY = new HashMap<>();
  private static final Map<String, UnaryOperator<String>> VALUE_CONVERTERS_GATEWAY =
      new HashMap<>();
  private static final Map<String, Replacement> MAPPING_BROKER = new HashMap<>();
  private static final Map<String, UnaryOperator<String>> VALUE_CONVERTERS_BROKER = new HashMap<>();
  private static final int DEFAULT_CONTACT_POINT_PORT = 26502;

  static { // static initialization for mapping tables

    // zeebe-gateway.network
    MAPPING_GATEWAY.put("ZEEBE_GATEWAY_HOST", new Replacement("zeebe-gateway.network.host"));
    MAPPING_GATEWAY.put("ZEEBE_GATEWAY_PORT", new Replacement("zeebe-gateway.network.port"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_KEEP_ALIVE_INTERVAL",
        new Replacement("zeebe-gateway.network.minKeepAliveInterval"));

    // zeebe-gateway.cluster
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_REQUEST_TIMEOUT", new Replacement("zeebe-gateway.cluster.requestTimeout"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CONTACT_POINT", new Replacement("zeebe-gateway.cluster.contactPoint"));
    VALUE_CONVERTERS_GATEWAY.put(
        "ZEEBE_GATEWAY_CONTACT_POINT",
        value -> value.contains(":") ? value : value + ":" + DEFAULT_CONTACT_POINT_PORT);
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CLUSTER_NAME", new Replacement("zeebe-gateway.cluster.clusterName"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CLUSTER_MEMBER_ID", new Replacement("zeebe-gateway.cluster.memberId"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CLUSTER_HOST", new Replacement("zeebe-gateway.cluster.host"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CLUSTER_PORT", new Replacement("zeebe-gateway.cluster.port"));

    // zeebe-gateway.monitoring
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_MONITORING_ENABLED", new Replacement("zeebe-gateway.monitoring.enadbled"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_MONITORING_HOST", new Replacement("zeebe-gateway.monitoring.host"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_MONITORING_PORT", new Replacement("zeebe-gateway.monitoring.port"));

    // zeebe-gateway.threads
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_MANAGEMENT_THREADS",
        new Replacement("zeebe-gateway.threads.managementThreads"));

    // zeebe-gateway.security
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_SECURITY_ENABLED", new Replacement("zeebe-gateway.security.enabled"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_CERTIFICATE_PATH",
        new Replacement("zeebe-gateway.security.certificateChainPath"));
    MAPPING_GATEWAY.put(
        "ZEEBE_GATEWAY_PRIVATE_KEY_PATH", new Replacement("zeebe-gateway.security.privateKeyPath"));

    // zeebe-broker ----------------------------------------
    MAPPING_BROKER.put("ZEEBE_STEP_TIMEOUT", new Replacement("zeebe-broker.stepTimeout"));

    // zeebe-broker.cluster
    MAPPING_BROKER.put("ZEEBE_NODE_ID", new Replacement("zeebe-broker.cluster.nodeId"));
    MAPPING_BROKER.put(
        "ZEEBE_CONTACT_POINTS", new Replacement("zeebe-broker.cluster.initialContactPoints"));
    MAPPING_BROKER.put(
        "ZEEBE_PARTITIONS_COUNT", new Replacement("zeebe-broker.cluster.partitionsCount"));
    MAPPING_BROKER.put(
        "ZEEBE_REPLICATION_FACTOR", new Replacement("zeebe-broker.cluster.replicationFactor"));
    MAPPING_BROKER.put("ZEEBE_CLUSTER_SIZE", new Replacement("zeebe-broker.cluster.clusterSize"));
    MAPPING_BROKER.put("ZEEBE_CLUSTER_NAME", new Replacement("zeebe-broker.cluster.clusterName"));

    // zeebe-broker.data
    MAPPING_BROKER.put("ZEEBE_DIRECTORIES", new Replacement("zeebe-broker.data.directories"));

    // zeebe-broker.gateway
    MAPPING_BROKER.put("ZEEBE_EMBED_GATEWAY", new Replacement("zeebe-broker.gateway.enable"));

    /* this essentially copies all entries from the gateway mapping table, but modifies
     * the new environment variables from "zeebe-gateway.*" to "zeebe-broker.gateway.*"
     */
    MAPPING_GATEWAY.forEach(LegacyConfigurationSupport::appendCorrespondingBrokerEntry);
    VALUE_CONVERTERS_BROKER.putAll(VALUE_CONVERTERS_GATEWAY);

    // zeebe-broker.network
    MAPPING_BROKER.put("ZEEBE_HOST", new Replacement("zeebe-broker.network.host"));
    MAPPING_BROKER.put(
        "ZEEBE_ADVERTISED_HOST", new Replacement("zeebe-broker.network.advertisedHost"));
    MAPPING_BROKER.put("ZEEBE_PORT_OFFSET", new Replacement("zeebe-broker.network.portOffset"));
  }

  private final Scope scope;

  public LegacyConfigurationSupport(final Scope scope) {
    this.scope = scope;
  }

  /**
   * This method checks whether the program was called with a toml configuration file. If so, it
   * prints out a warning.
   */
  public void checkForLegacyTomlConfigurationArgument(
      final String[] args, final String recommendedSetting) {
    if (args.length == 1 && args[0].endsWith(".toml")) {
      final String configFileArgument = args[0];
      Loggers.LEGACY_LOGGER.warn(
          "Found command line argument "
              + configFileArgument
              + " which might be a TOML configuration file.");
      Loggers.LEGACY_LOGGER.info(
          "TOML configuration files are no longer supported. Please specify a YAML configuration file"
              + "and set it via environment variable \"spring.config.additional-location\" (e.g. "
              + "\"export spring.config.additional-location='file:./config/"
              + recommendedSetting
              + "'\").");
      Loggers.LEGACY_LOGGER.info(
          "The ./config/ folder contains a configuration file template. Alternatively, you can also use environment variables.");
    }
  }

  /**
   * This method checks for legacy environment variables. If it finds an old environment variable
   * which is set, it looks whether the new counterpart is set. If the new counterpart is set, it
   * does nothing. If it is not set, it sets a system setting under the new key that has the same
   * value as is associated with the old environment variable. This effectively makes the old
   * environment variable value visible under the new environment variable. It also prints out a
   * warning to the log.
   */
  public void checkForLegacyEnvironmentVariables() {

    final Map<String, Replacement> mappingTable;
    final Map<String, UnaryOperator<String>> valueConverters;

    switch (scope) {
      case GATEWAY:
        mappingTable = MAPPING_GATEWAY;
        valueConverters = VALUE_CONVERTERS_GATEWAY;
        break;

      case BROKER:
        mappingTable = MAPPING_BROKER;
        valueConverters = VALUE_CONVERTERS_BROKER;
        break;
      default:
        throw new IllegalStateException("Unexpected value: " + scope);
    }

    for (Entry<String, Replacement> mapping : mappingTable.entrySet()) {
      final String oldEnvironmentVariable = mapping.getKey();
      final Replacement replacement = mapping.getValue();

      if (isEnvironmentVariableSet(oldEnvironmentVariable) && !isReplacementUsed(replacement)) {
        Loggers.LEGACY_LOGGER.warn(
            "Found use of legacy system environment variable '"
                + oldEnvironmentVariable
                + "'. Please use '"
                + replacement.toEnvironmentVariable()
                + "' instead.");
        Loggers.LEGACY_LOGGER.info(
            "The old environment variable is currently supported as part of our backwards compatibility goals.");
        Loggers.LEGACY_LOGGER.info(
            "However, please note that support for the old environment variable is scheduled to be removed for release 0.25.0.");

        String value = System.getenv(oldEnvironmentVariable);

        final Function<String, String> valueConverter = valueConverters.get(oldEnvironmentVariable);

        if (valueConverter != null) {
          final String oldValue = value;
          value = valueConverter.apply(oldValue);

          Loggers.LEGACY_LOGGER.warn(
              "The old implementation performed an implicit value conversion from '"
                  + oldValue
                  + "' (as given in the environment variable) to '"
                  + value
                  + "' (as applied to the configuration). This automatic conversion will also be removed.");
        }

        System.setProperty(replacement.getCanonicalRepresentation(), value);
      }
    }
  }

  private boolean isEnvironmentVariableSet(String variable) {
    return System.getenv(variable) != null || System.getProperty(variable) != null;
  }

  private boolean isReplacementUsed(Replacement replacement) {
    return System.getenv(replacement.getCanonicalRepresentation()) != null
        // in env vars the canonical representation could be used
        || System.getenv(replacement.toEnvironmentVariable()) != null
        // in env vars the environment variable representation could be used
        || System.getProperty(replacement.getCanonicalRepresentation()) != null;
    // in system properties, only the canonical representation can be used; the environment variable
    // representation can not be used
  }

  /**
   * This essentially copies an entry from the gateway mapping table into the broker mapping table,
   * but modifies the new environment variables from "zeebe-gateway.*" to "zeebe-broker.gateway.*"
   */
  private static void appendCorrespondingBrokerEntry(
      String oldEnvironmentVariable, Replacement replacement) {
    final String newEnvironmentVariableInGatewayContext = replacement.getCanonicalRepresentation();

    assert newEnvironmentVariableInGatewayContext.startsWith("zeebe-");
    final int insertionPoint = "zeebe-".length();

    final String newEnvironmentVariableInBrokerContext =
        newEnvironmentVariableInGatewayContext.substring(0, insertionPoint) // "zeebe-"
            + "broker."
            + newEnvironmentVariableInGatewayContext.substring(insertionPoint); // "gateway.*"

    MAPPING_BROKER.put(
        oldEnvironmentVariable, new Replacement(newEnvironmentVariableInBrokerContext));
  }

  public enum Scope {
    GATEWAY,
    BROKER
  }

  static final class Replacement {
    private final String canonicalRepresentation;

    Replacement(String canonicalRepresentation) {
      this.canonicalRepresentation = canonicalRepresentation;
    }

    private String getCanonicalRepresentation() {
      return canonicalRepresentation;
    }

    String toEnvironmentVariable() {
      return canonicalRepresentation.replace("-", "").replace(".", "_").toUpperCase();
    }
  }
}
