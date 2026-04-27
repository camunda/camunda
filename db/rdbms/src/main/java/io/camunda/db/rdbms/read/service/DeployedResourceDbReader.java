/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.DeployedResourceMapper;
import io.camunda.search.clients.reader.DeployedResourceReader;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.security.reader.ResourceAccessChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployedResourceDbReader implements DeployedResourceReader {

  private static final Logger LOG = LoggerFactory.getLogger(DeployedResourceDbReader.class);

  private final DeployedResourceMapper deployedResourceMapper;

  public DeployedResourceDbReader(final DeployedResourceMapper deployedResourceMapper) {
    this.deployedResourceMapper = deployedResourceMapper;
  }

  @Override
  public DeployedResourceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Get resource with resource key {}", key);
    return deployedResourceMapper.get(key);
  }

  @Override
  public DeployedResourceEntity getByKeyMetadata(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Get resource metadata (no content) with resource key {}", key);
    return deployedResourceMapper.getMetadata(key);
  }
}
