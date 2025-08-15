/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db.indices;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class VariableUpdateInstanceIndexOld<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String INSTANCE_ID = "instanceId";
  public static final String NAME = "name";
  public static final String TYPE = "type";
  public static final String VALUE = "value";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String TENANT_ID = "tenantId";
  public static final String TIMESTAMP = "timestamp";
  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return "variable-update-instance";
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return "-000001";
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties("instanceId", (p) -> p.keyword((k) -> k))
        .properties("name", (p) -> p.keyword((k) -> k))
        .properties("type", (p) -> p.keyword((k) -> k))
        .properties("value", (p) -> p.keyword((k) -> k))
        .properties("processInstanceId", (p) -> p.keyword((k) -> k))
        .properties("tenantId", (p) -> p.keyword((k) -> k))
        .properties("timestamp", (p) -> p.date((k) -> k.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));
  }
}
