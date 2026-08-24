/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.Rdbms;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * Provides a physical tenant's database vendor id — the key selecting its {@code
 * db/vendor-properties/<id>.properties} and its vendor-specific mapper statements.
 *
 * <p>{@link #resolve} answers from static configuration alone: an explicit {@code
 * database-vendor-id}, else the tenant's JDBC URL. That is what lets a tenant whose database is
 * unreachable be built at all — the tenant's whole object graph is constructed before schema
 * initialization starts, so as long as no step in constructing it connects, an unreachable database
 * degrades that one tenant instead of failing the context refresh for every tenant on the node.
 *
 * <p>{@link #fromDatabaseProductName} is the fallback for the URL prefixes that leaves unresolved —
 * jTDS, proxy wrappers. A purely static rule would refuse to start such a deployment until it set
 * the property, so it keeps starting exactly as before, at the cost of the guarantee above. How
 * much that costs depends on how many tenants share the node, which is why the choice between the
 * two, and the reporting that goes with it, belongs to the caller.
 */
@NullMarked
final class RdbmsVendorIdProvider {

  static final String VENDOR_ID_PROPERTY =
      "camunda.data.secondary-storage.rdbms.database-vendor-id";

  private static final Logger LOGGER = LoggerFactory.getLogger(RdbmsVendorIdProvider.class);

  /**
   * The vendors this application ships properties and mapper statements for. {@link
   * DatabaseDriver#getId()} is deliberately not used: it answers {@code sqlserver} where every
   * other layer here says {@code mssql}.
   */
  private static final Map<DatabaseDriver, String> VENDOR_ID_BY_DRIVER = vendorIdsByDriver();

  /** The legal values of {@link #VENDOR_ID_PROPERTY}, in a stable order for error messages. */
  private static final String LEGAL_VENDOR_IDS = String.join(", ", VENDOR_ID_BY_DRIVER.values());

  /**
   * MyBatis' own vendor detection, mapping the product names it reads to the ids this application
   * uses. It matches with {@link String#contains}, which is why a driver reporting {@code
   * "Microsoft SQL Server 2019"} resolves — {@link DatabaseDriver#fromProductName} compares for
   * equality and would not.
   */
  private static final VendorDatabaseIdProvider PRODUCT_NAME_PROVIDER = productNameProvider();

  private RdbmsVendorIdProvider() {}

  /**
   * The vendor id as far as configuration settles it, without any I/O.
   *
   * @return empty if neither the {@code database-vendor-id} property nor the JDBC URL names a
   *     vendor, which is the only case that costs a connection
   * @throws IllegalArgumentException if the configured vendor id is not one of {@link
   *     #LEGAL_VENDOR_IDS}
   */
  static Optional<String> resolve(final String physicalTenantId, final Rdbms rdbms) {
    final var configured = StringUtils.trimToNull(rdbms.getDatabaseVendorId());
    if (configured != null) {
      final var validated = validate(configured, physicalTenantId);
      LOGGER.info(
          "Using the configured database vendor id '{}' for physical tenant '{}'.",
          validated,
          physicalTenantId);
      return Optional.of(validated);
    }

    final var url = rdbms.getUrl();
    final var fromUrl =
        VENDOR_ID_BY_DRIVER.get(unwrapVendorDriver(DatabaseDriver.fromJdbcUrl(url), url));
    if (fromUrl != null) {
      LOGGER.info(
          "Resolved database vendor id '{}' for physical tenant '{}' from its JDBC URL. Set {} if"
              + " the server behind that URL is a different vendor.",
          fromUrl,
          physicalTenantId,
          VENDOR_ID_PROPERTY);
    }
    return Optional.ofNullable(fromUrl);
  }

  /**
   * The vendor id read from the database itself, over one connection.
   *
   * @return empty if the product name it reports is not one this application ships properties for
   * @throws RuntimeException if the database cannot be reached
   */
  static Optional<String> fromDatabaseProductName(final DataSource dataSource) {
    return Optional.ofNullable(PRODUCT_NAME_PROVIDER.getDatabaseId(dataSource));
  }

  /**
   * Reports a vendor no step could name, which is a configuration error rather than a degraded
   * tenant: without a vendor id there is no {@code SqlSessionFactory}, and so no tenant to degrade.
   *
   * @param whyNotDetected completes "… from JDBC URL '…' <em>&lt;whyNotDetected&gt;</em>; set …"
   */
  static IllegalArgumentException unresolvable(
      final String physicalTenantId,
      final String url,
      final String whyNotDetected,
      final @Nullable Throwable cause) {
    final var message =
        "Cannot determine the database vendor for physical tenant '%s' from JDBC URL '%s' %s; set %s to one of %s."
            .formatted(physicalTenantId, url, whyNotDetected, VENDOR_ID_PROPERTY, LEGAL_VENDOR_IDS);
    return cause == null
        ? new IllegalArgumentException(message)
        : new IllegalArgumentException(message, cause);
  }

  /**
   * Resolves the underlying database vendor from a JDBC URL, unwrapping the AWS Advanced JDBC
   * Wrapper's {@code jdbc:aws-wrapper:<vendor>://...} URL scheme. Without this, {@link
   * DatabaseDriver#fromJdbcUrl} resolves such URLs to {@link DatabaseDriver#AWS_WRAPPER} rather
   * than the wrapped vendor, so neither {@link #resolve}'s URL step nor the driver-specific
   * connection properties keyed off the same answer would ever apply to an Aurora failover-aware
   * connection.
   */
  static DatabaseDriver unwrapVendorDriver(final DatabaseDriver driver, final String url) {
    if (driver != DatabaseDriver.AWS_WRAPPER) {
      return driver;
    }
    return DatabaseDriver.fromJdbcUrl(url.replaceFirst("(?i)^jdbc:aws-wrapper:", "jdbc:"));
  }

  private static String validate(final String configuredVendorId, final String physicalTenantId) {
    if (!VENDOR_ID_BY_DRIVER.containsValue(configuredVendorId)) {
      throw new IllegalArgumentException(
          "Invalid database vendor id '%s' configured for physical tenant '%s'; set %s to one of %s."
              .formatted(
                  configuredVendorId, physicalTenantId, VENDOR_ID_PROPERTY, LEGAL_VENDOR_IDS));
    }
    return configuredVendorId;
  }

  private static Map<DatabaseDriver, String> vendorIdsByDriver() {
    final var byDriver = new LinkedHashMap<DatabaseDriver, String>();
    byDriver.put(DatabaseDriver.H2, "h2");
    byDriver.put(DatabaseDriver.POSTGRESQL, "postgresql");
    byDriver.put(DatabaseDriver.ORACLE, "oracle");
    byDriver.put(DatabaseDriver.MARIADB, "mariadb");
    byDriver.put(DatabaseDriver.MYSQL, "mysql");
    byDriver.put(DatabaseDriver.SQLSERVER, "mssql");
    return Collections.unmodifiableMap(byDriver);
  }

  private static VendorDatabaseIdProvider productNameProvider() {
    final var productNames = new Properties();
    productNames.put("H2", "h2");
    productNames.put("PostgreSQL", "postgresql");
    productNames.put("Oracle", "oracle");
    productNames.put("MariaDB", "mariadb");
    productNames.put("MySQL", "mysql");
    productNames.put("Microsoft SQL Server", "mssql");
    final var provider = new VendorDatabaseIdProvider();
    provider.setProperties(productNames);
    return provider;
  }
}
