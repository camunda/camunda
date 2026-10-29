/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.typehandler.EntityProtectionModeTypeHandler;
import java.io.IOException;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** One physical tenant's parsed MyBatis configuration and the session it runs statements on. */
@NullMarked
record PhysicalTenantMyBatis(
    SqlSessionFactory sqlSessionFactory, SqlSessionTemplate sqlSessionTemplate) {

  static PhysicalTenantMyBatis build(
      final DataSource dataSource,
      final VendorDatabaseProperties databaseProperties,
      final @Nullable String prefix) {
    final var factory = buildSqlSessionFactory(dataSource, databaseProperties, prefix);
    return new PhysicalTenantMyBatis(factory, new SqlSessionTemplate(factory));
  }

  private static SqlSessionFactory buildSqlSessionFactory(
      final DataSource dataSource,
      final VendorDatabaseProperties databaseProperties,
      final @Nullable String prefix) {
    final var configuration = new Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);
    configuration.getTypeHandlerRegistry().register(EntityProtectionModeTypeHandler.class);
    // Which vendor's mapper statements apply is already settled in the vendor properties, so it is
    // set here rather than handed to a DatabaseIdProvider that would look it up over a connection
    // a second time. SqlSessionFactoryBean would only call setDatabaseId on this same object.
    configuration.setDatabaseId(databaseProperties.databaseId());

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(dataSource);
    final SqlSessionFactory factory;
    try {
      factoryBean.addMapperLocations(
          new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

      final Properties p = new Properties();
      p.put("prefix", StringUtils.trimToEmpty(prefix));
      p.putAll(databaseProperties.properties());
      factoryBean.setConfigurationProperties(p);
      factory = factoryBean.getObject();
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read the MyBatis mapper files", e);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to build the MyBatis session factory", e);
    }
    // The bulk of the memory usage (~50-60%) for MyBatis with high tenant count is coming from the
    // sql fragments.
    // The <sql> fragments are only needed to resolve <include>s while the mappers are parsed, which
    // is done by now. Each one holds on to the whole parsed mapper DOM, which is otherwise retained
    // for the lifetime of the tenant's factory, once per physical tenant on every node.
    configuration.getSqlFragments().clear();
    return factory;
  }
}
