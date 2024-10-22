/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import java.io.IOException;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Import(DataSourceAutoConfiguration.class)
public class MyBatisConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisConfiguration.class);

  @Bean
  public MultiTenantSpringLiquibase customerLiquibase(final DataSource dataSource) {
    LOGGER.info("Initializing Liquibase for RDBMS.");
    final var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    // changelog file located in src/main/resources directly in the module
    moduleConfig.setChangeLog("db/changelog/rdbms-support/changelog-master.xml");
    return moduleConfig;
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(final DataSource dataSource) throws Exception {
    final var vendorProperties = new Properties();
    vendorProperties.put("H2", "h2");
    vendorProperties.put("PostgreSQL", "postgresql");
    vendorProperties.put("Oracle", "oracle");
    vendorProperties.put("MariaDB", "mariadb");
    vendorProperties.put("MySQL", "mariadb");
    vendorProperties.put("SQL Server", "sqlserver");
    final var databaseIdProvider = new VendorDatabaseIdProvider();
    databaseIdProvider.setProperties(vendorProperties);

    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    factoryBean.setDatabaseIdProvider(databaseIdProvider);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    // load vendor specific template variables
    final var databaseId = databaseIdProvider.getDatabaseId(dataSource);
    LOGGER.info("Detected databaseId: {}", databaseId);
    final Properties p = getVendorProperties(databaseIdProvider.getDatabaseId(dataSource));

    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<FlowNodeInstanceMapper> flowNodeInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, FlowNodeInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessDefinitionMapper> processDeploymentMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, VariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserTaskMapper> userTaskMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UserTaskMapper.class);
  }

  @Bean
  public MapperFactoryBean<ExporterPositionMapper> exporterPosition(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ExporterPositionMapper.class);
  }

  private <T> MapperFactoryBean<T> createMapperFactoryBean(
      final SqlSessionFactory sqlSessionFactory, final Class<T> clazz) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(clazz);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }

  private Properties getVendorProperties(final String vendorId) throws IOException {
    final Properties properties = new Properties();
    final var file = "db/vendor-properties/" + vendorId + ".properties";
    try (final var propertiesInputStream = getClass().getClassLoader().getResourceAsStream(file)) {
      if (propertiesInputStream != null) {
        properties.load(propertiesInputStream);
      } else {
        LOGGER.debug("No vendor properties found for databaseId {}", vendorId);
      }
    }
    return properties;
  }
}
