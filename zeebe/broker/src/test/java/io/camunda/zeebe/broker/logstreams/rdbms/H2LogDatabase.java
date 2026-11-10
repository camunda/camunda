/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;

/**
 * A fresh in-memory H2 database with the real RDBMS schema, and a session factory for the log
 * mapper configured as the broker's is: the table prefix and H2's vendor properties as variables.
 */
final class H2LogDatabase {

  private static final String MAPPER = "mapper/LogBlockMapper.xml";

  private H2LogDatabase() {}

  static SqlSessionFactory create(final String prefix) throws Exception {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:log-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    final var vendorProperties = VendorDatabasePropertiesLoader.load("h2");
    new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(dataSource, vendorProperties, prefix, true, null), "8.11.0")
        .initialize();

    final var configuration =
        new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
    configuration.setDatabaseId(vendorProperties.databaseId());
    final var variables = new Properties();
    variables.putAll(vendorProperties.properties());
    variables.put("prefix", prefix);
    configuration.setVariables(variables);
    try (final var mapper = H2LogDatabase.class.getClassLoader().getResourceAsStream(MAPPER)) {
      new XMLMapperBuilder(mapper, configuration, MAPPER, configuration.getSqlFragments()).parse();
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }
}
