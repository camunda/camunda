/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.SecurityConfiguration;
import io.camunda.zeebe.util.ReflectUtil;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class OperateConnectConfiguration extends ConnectConfiguration {
  private int numberOfShards;
  private int numberOfReplicas;
  private int batchSize;
  private Boolean createSchema;
  private Boolean healthCheckEnabled;

  public OperateConnectConfiguration(
      final ConnectConfiguration connectConfiguration,
      final OperateElasticsearchProperties elasticsearchProperties,
      final OperateOpensearchProperties opensearchProperties) {
    super();
    // set the defaults from the operate properties
    switch (connectConfiguration.getTypeEnum()) {
      case ELASTICSEARCH -> mergeWithElasticProperties(elasticsearchProperties);
      case OPENSEARCH -> mergeWithOpensearchProperties(opensearchProperties);
    }
    // merge the configured settings from ConnectConfiguration
    mergeWithConnectConfiguration(connectConfiguration);
  }

  private void mergeWithConnectConfiguration(final ConnectConfiguration connectConfiguration) {
    final var defaultInstance = new ConnectConfiguration();
    final var fields =
        Arrays.stream(ConnectConfiguration.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .toList();

    for (final var field : fields) {
      try {
        ReflectUtil.makeAccessible(field, connectConfiguration);
        ReflectUtil.makeAccessible(field, defaultInstance);
        final var defaultValue = field.get(defaultInstance);
        final var setValue = field.get(connectConfiguration);
        // set value only if it's not the same as the default
        // if value is primitive, compare with ==
        if (field.getType().isPrimitive()) {
          if (setValue != defaultValue) {
            field.set(this, setValue);
          }
        } else {
          // compare with .equals()
          if (!Objects.equals(setValue, defaultValue)) {
            field.set(this, setValue);
          }
        }
      } catch (final IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void mergeWithElasticProperties(final OperateElasticsearchProperties props) {
    setIfConfigured(props::getIndexPrefix, this::setIndexPrefix);
    setIfConfiguredInt(props::getNumberOfShards, this::setNumberOfShards);
    setIfConfiguredInt(props::getNumberOfReplicas, this::setNumberOfReplicas);
    setIfConfigured(props::getClusterName, this::setClusterName);
    setIfConfigured(props::getDateFormat, this::setDateFormat);
    setIfConfigured(props::getElsDateFormat, this::setFieldDateFormat);
    setIfConfiguredInt(props::getBatchSize, this::setBatchSize);
    setIfConfigured(props::getSocketTimeout, this::setSocketTimeout);
    setIfConfigured(props::getConnectTimeout, this::setConnectTimeout);
    setIfConfigured(props::isCreateSchema, this::setCreateSchema);
    setIfConfigured(props::isHealthCheckEnabled, this::setHealthCheckEnabled);
    setIfConfigured(props::getUrl, this::setUrl);
    setIfConfigured(props::getUsername, this::setUsername);
    setIfConfigured(props::getPassword, this::setPassword);
    setIfConfigured(
        props::getSsl,
        this::setSecurity,
        ssl -> {
          final var security = new SecurityConfiguration();
          // fixme
          return security;
        });
    setIfConfigured(props::getInterceptorPlugins, this::setInterceptorPlugins);
  }

  private void mergeWithOpensearchProperties(final OperateOpensearchProperties props) {
    setIfConfigured(props::getIndexPrefix, this::setIndexPrefix);
    setIfConfiguredInt(props::getNumberOfShards, this::setNumberOfShards);
    setIfConfiguredInt(props::getNumberOfReplicas, this::setNumberOfReplicas);
    setIfConfigured(props::getClusterName, this::setClusterName);
    setIfConfigured(props::getDateFormat, this::setDateFormat);
    setIfConfigured(props::getOsDateFormat, this::setFieldDateFormat);
    setIfConfiguredInt(props::getBatchSize, this::setBatchSize);
    setIfConfigured(props::getSocketTimeout, this::setSocketTimeout);
    setIfConfigured(props::getConnectTimeout, this::setConnectTimeout);
    setIfConfigured(props::isCreateSchema, this::setCreateSchema);
    setIfConfigured(props::isHealthCheckEnabled, this::setHealthCheckEnabled);
    setIfConfigured(props::getUrl, this::setUrl);
    setIfConfigured(props::getUsername, this::setUsername);
    setIfConfigured(props::getPassword, this::setPassword);
    setIfConfigured(
        props::getSsl,
        this::setSecurity,
        ssl -> {
          final var security = new SecurityConfiguration();
          // fixme
          return security;
        });
    setIfConfigured(props::getInterceptorPlugins, this::setInterceptorPlugins);
  }

  private <A, B> void setIfConfigured(
      final Supplier<A> getter, final Consumer<B> setter, final Function<A, B> converter) {
    if (getter.get() != null) {
      final B converted = converter.apply(getter.get());
      setter.accept(converted);
    }
  }

  private <A> void setIfConfigured(final Supplier<A> getter, final Consumer<A> setter) {
    setIfConfigured(getter, setter, Function.identity());
  }

  private void setIfConfiguredInt(final IntSupplier getter, final IntConsumer setter) {
    if (getter.getAsInt() >= 0) {
      setter.accept(getter.getAsInt());
    }
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Boolean getCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(final Boolean createSchema) {
    this.createSchema = createSchema;
  }

  public Boolean isHealthCheckEnabled() {
    return healthCheckEnabled;
  }

  public void setHealthCheckEnabled(final Boolean healthCheckEnabled) {
    this.healthCheckEnabled = healthCheckEnabled;
  }

  public int getNumberOfShards() {
    return numberOfShards;
  }

  public void setNumberOfShards(final int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return numberOfReplicas;
  }

  public void setNumberOfReplicas(final int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }
}
