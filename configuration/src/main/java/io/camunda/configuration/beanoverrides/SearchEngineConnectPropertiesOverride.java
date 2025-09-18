/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageDatabase;
import io.camunda.configuration.Security;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@DependsOn("unifiedConfigurationHelper")
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineConnectPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;

  public SearchEngineConnectPropertiesOverride(
      @Autowired final UnifiedConfiguration unifiedConfiguration) {
    this.unifiedConfiguration = unifiedConfiguration;
  }

  @Bean
  @Primary
  public SearchEngineConnectProperties searchEngineConnectProperties() {
    final SearchEngineConnectProperties override = new SearchEngineConnectProperties();

    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    final SecondaryStorageDatabase database;

    switch (secondaryStorage.getType()) {
      case elasticsearch:
        {
          database = secondaryStorage.getElasticsearch();
          break;
        }
      case rdbms:
        {
          database = secondaryStorage.getRdbms();
          break;
        }
      default:
        {
          database = secondaryStorage.getOpensearch();
          break;
        }
    }

    override.setType(secondaryStorage.getType().name());
    override.setUrl(database.getUrl());
    override.setClusterName(database.getClusterName());

    populateFromSecurity(override);
    override.setIndexPrefix(database.getIndexPrefix());

    override.setUsername(database.getUsername());
    override.setPassword(database.getPassword());

    return override;
  }

  private void populateFromSecurity(final SearchEngineConnectProperties override) {
    final Security security = resolveSecurity(override);
    if (security == null) {
      return;
    }

    override.getSecurity().setEnabled(security.isEnabled());
    override.getSecurity().setCertificatePath(security.getCertificatePath());
    override.getSecurity().setVerifyHostname(security.isVerifyHostname());
    override.getSecurity().setSelfSigned(security.isSelfSigned());
  }

  private Security resolveSecurity(final SearchEngineConnectProperties override) {
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    return switch (override.getTypeEnum()) {
      case ELASTICSEARCH -> secondaryStorage.getElasticsearch().getSecurity();
      case OPENSEARCH -> secondaryStorage.getOpensearch().getSecurity();
      default -> null;
    };
  }
}
