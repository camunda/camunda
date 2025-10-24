/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.it.rdbms.exporter.RdbmsTestConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
class RdbmsDatasourcePropertyForwardingIT {

  public static final long MAX_POOL_SIZE = 42L;
  public static final long MAX_POOL_SIZE_DEFAULT = 10L;
  public static final long MINIMUM_IDLE = 2L;
  public static final long MINIMUM_IDLE_DEFAULT = 10L;
  public static final long IDLE_TIMEOUT = 123456L;
  public static final long IDLE_TIMEOUT_DEFAULT = 600000L;
  public static final long MAX_LIFETIME = 18999999L;
  public static final long MAX_LIFETIME_DEFAULT = 1800000L;
  public static final long CONNECTION_TIMEOUT = 666L;
  public static final long CONNECTION_TIMEOUT_DEFAULT = 30000L;
  public static final String URL = "jdbc:h2:mem:test";
  public static final String USERNAME = "camunda";
  public static final String PASSWORD = "camundaPW";

  @Nested
  @TestPropertySource(
      properties = {
        "spring.liquibase.enabled=false",
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.prefix=C8_",
        "camunda.data.secondary-storage.rdbms.url=" + RdbmsDatasourcePropertyForwardingIT.URL,
        "camunda.data.secondary-storage.rdbms.username="
            + RdbmsDatasourcePropertyForwardingIT.USERNAME,
        "camunda.data.secondary-storage.rdbms.password="
            + RdbmsDatasourcePropertyForwardingIT.PASSWORD,
        "camunda.data.secondary-storage.rdbms.connection-pool.maximum-pool-size="
            + RdbmsDatasourcePropertyForwardingIT.MAX_POOL_SIZE,
        "camunda.data.secondary-storage.rdbms.connection-pool.minimum-idle="
            + RdbmsDatasourcePropertyForwardingIT.MINIMUM_IDLE,
        "camunda.data.secondary-storage.rdbms.connection-pool.idle-timeout="
            + RdbmsDatasourcePropertyForwardingIT.IDLE_TIMEOUT,
        "camunda.data.secondary-storage.rdbms.connection-pool.max-lifetime="
            + RdbmsDatasourcePropertyForwardingIT.MAX_LIFETIME,
        "camunda.data.secondary-storage.rdbms.connection-pool.connection-timeout="
            + RdbmsDatasourcePropertyForwardingIT.CONNECTION_TIMEOUT,
        "logging.level.com.zaxxer.hikari=DEBUG"
      })
  class TestHikariPropertiesOverride {

    @Autowired private DataSource dataSource;

    @Test
    void testForwardingOfSpringDatasourceProperties() {
      assertThat(((HikariDataSource) dataSource).getJdbcUrl()).isEqualTo(URL);
      assertThat(((HikariDataSource) dataSource).getUsername()).isEqualTo(USERNAME);
      assertThat(((HikariDataSource) dataSource).getPassword()).isEqualTo(PASSWORD);
      assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(MAX_POOL_SIZE);
      assertThat(((HikariDataSource) dataSource).getMinimumIdle()).isEqualTo(MINIMUM_IDLE);
      assertThat(((HikariDataSource) dataSource).getIdleTimeout()).isEqualTo(IDLE_TIMEOUT);
      assertThat(((HikariDataSource) dataSource).getMaxLifetime()).isEqualTo(MAX_LIFETIME);
      assertThat(((HikariDataSource) dataSource).getConnectionTimeout())
          .isEqualTo(CONNECTION_TIMEOUT);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "spring.liquibase.enabled=false",
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.secondary-storage.rdbms.prefix=C8_",
        "camunda.data.secondary-storage.rdbms.url=" + RdbmsDatasourcePropertyForwardingIT.URL,
        "camunda.data.secondary-storage.rdbms.username="
            + RdbmsDatasourcePropertyForwardingIT.USERNAME,
        "camunda.data.secondary-storage.rdbms.password="
            + RdbmsDatasourcePropertyForwardingIT.PASSWORD,
        "logging.level.com.zaxxer.hikari=DEBUG"
      })
  class TestHikariPropertiesDefault {

    @Autowired private DataSource dataSource;

    @Test
    void testHikariDefaultValues() {
      assertThat(((HikariDataSource) dataSource).getJdbcUrl()).isEqualTo(URL);
      assertThat(((HikariDataSource) dataSource).getUsername()).isEqualTo(USERNAME);
      assertThat(((HikariDataSource) dataSource).getPassword()).isEqualTo(PASSWORD);
      assertThat(((HikariDataSource) dataSource).getMaximumPoolSize())
          .isEqualTo(MAX_POOL_SIZE_DEFAULT);
      assertThat(((HikariDataSource) dataSource).getMinimumIdle()).isEqualTo(MINIMUM_IDLE_DEFAULT);
      assertThat(((HikariDataSource) dataSource).getIdleTimeout()).isEqualTo(IDLE_TIMEOUT_DEFAULT);
      assertThat(((HikariDataSource) dataSource).getMaxLifetime()).isEqualTo(MAX_LIFETIME_DEFAULT);
      assertThat(((HikariDataSource) dataSource).getConnectionTimeout())
          .isEqualTo(CONNECTION_TIMEOUT_DEFAULT);
    }
  }
}
