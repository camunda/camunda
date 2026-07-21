/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.configuration.Aws;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride.Converter;
import io.camunda.search.connect.aws.AwsCredentialsProviders;
import io.camunda.search.connect.configuration.AwsConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import software.amazon.awssdk.services.rds.RdsUtilities;

/**
 * {@link DataSource} authenticating via AWS IAM database authentication (Aurora/RDS): each new
 * connection uses a freshly generated, short-lived authentication token as the password. Existing
 * connections stay authenticated, so token expiry only affects connection creation.
 */
public final class RdsIamAuthDataSource implements DataSource {

  private static final Pattern JDBC_URL =
      Pattern.compile("^jdbc:(?<vendor>[a-z0-9]+)://(?<host>[^/:?]+)(?::(?<port>\\d+))?.*");
  private static final Map<String, Integer> DEFAULT_PORTS =
      Map.of("postgresql", 5432, "mysql", 3306, "mariadb", 3306);

  private final String jdbcUrl;
  private final String username;
  private final String hostname;
  private final int port;
  private final RdsUtilities rdsUtilities;

  private RdsIamAuthDataSource(
      final String jdbcUrl,
      final String username,
      final String hostname,
      final int port,
      final RdsUtilities rdsUtilities) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.hostname = hostname;
    this.port = port;
    this.rdsUtilities = rdsUtilities;
  }

  public static RdsIamAuthDataSource of(final Rdbms rdbms, final Aws aws) {
    final var awsConfiguration = new AwsConfiguration();
    Converter.populateAws(aws, awsConfiguration);
    final var rdsUtilities =
        RdsUtilities.builder()
            .credentialsProvider(AwsCredentialsProviders.from(awsConfiguration))
            .region(AwsCredentialsProviders.region(awsConfiguration))
            .build();
    final var url = rdbms.getUrl();
    final var matcher = JDBC_URL.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Cannot derive hostname and port for AWS IAM authentication from JDBC URL '%s'"
              .formatted(url));
    }
    final var portGroup = matcher.group("port");
    final var defaultPort = DEFAULT_PORTS.get(matcher.group("vendor"));
    if (portGroup == null && defaultPort == null) {
      throw new IllegalArgumentException(
          "JDBC URL '%s' must declare an explicit port for AWS IAM authentication".formatted(url));
    }
    final var port = portGroup != null ? Integer.parseInt(portGroup) : defaultPort;
    return new RdsIamAuthDataSource(
        url, rdbms.getUsername(), matcher.group("host"), port, rdsUtilities);
  }

  @VisibleForTesting
  String generateToken() {
    return rdsUtilities.generateAuthenticationToken(
        builder -> builder.hostname(hostname).port(port).username(username));
  }

  @Override
  public Connection getConnection() throws SQLException {
    final var properties = new Properties();
    properties.setProperty("user", username);
    properties.setProperty("password", generateToken());
    return DriverManager.getConnection(jdbcUrl, properties);
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    throw new SQLFeatureNotSupportedException(
        "AWS IAM authentication does not support explicit credentials");
  }

  @Override
  public PrintWriter getLogWriter() {
    return null;
  }

  @Override
  public void setLogWriter(final PrintWriter out) {}

  @Override
  public int getLoginTimeout() {
    return 0;
  }

  @Override
  public void setLoginTimeout(final int seconds) {}

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Cannot unwrap to " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) {
    return iface.isInstance(this);
  }
}
