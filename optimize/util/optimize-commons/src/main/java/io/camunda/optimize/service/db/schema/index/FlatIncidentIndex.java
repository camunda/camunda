/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import java.util.Locale;

/**
 * A flattened incident index that stores IncidentDto with required process definition fields. This
 * index is designed for efficient aggregations in a multi-stage pipeline.
 */
public abstract class FlatIncidentIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 1;
  public static final String FLAT_INCIDENT_INDEX_PREFIX = "flat-incident-";

  // Required process fields
  public static final String PROCESS_DEFINITION_KEY =
      ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION =
      ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = IncidentDto.Fields.processInstanceId;

  // Incident specific fields
  public static final String INCIDENT_ID = IncidentDto.Fields.id;
  public static final String CREATE_TIME = IncidentDto.Fields.createTime;
  public static final String END_TIME = IncidentDto.Fields.endTime;
  public static final String DURATION_IN_MS = IncidentDto.Fields.durationInMs;
  public static final String INCIDENT_TYPE = IncidentDto.Fields.incidentType;
  public static final String ACTIVITY_ID = IncidentDto.Fields.activityId;
  public static final String FAILED_ACTIVITY_ID = IncidentDto.Fields.failedActivityId;
  public static final String INCIDENT_MESSAGE = IncidentDto.Fields.incidentMessage;
  public static final String INCIDENT_STATUS = IncidentDto.Fields.incidentStatus;
  public static final String DEFINITION_KEY = IncidentDto.Fields.definitionKey;
  public static final String DEFINITION_VERSION = IncidentDto.Fields.definitionVersion;
  public static final String TENANT_ID = IncidentDto.Fields.tenantId;
  public static final String PARTITION = "partition";

  private final String indexName;

  protected FlatIncidentIndex(final String incidentIndexKey) {
    super(incidentIndexKey);
    indexName = getIndexPrefix() + incidentIndexKey.toLowerCase(Locale.ENGLISH);
  }

  public static String constructIndexName(final String incidentIndexKey) {
    return FLAT_INCIDENT_INDEX_PREFIX + incidentIndexKey.toLowerCase(Locale.ENGLISH);
  }

  /**
   * Constructs the index name using both the process definition key and the ordinal tick string
   * (e.g. {@code "20260306-1430"}). The ordinal tick is mandatory.
   */
  public static String constructIndexName(final String incidentIndexKey, final String ordinalTick) {
    return FLAT_INCIDENT_INDEX_PREFIX
        + incidentIndexKey.toLowerCase(Locale.ENGLISH)
        + "-"
        + ordinalTick;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getDefinitionKeyFieldName() {
    return PROCESS_DEFINITION_KEY;
  }

  @Override
  public String getDefinitionVersionFieldName() {
    return PROCESS_DEFINITION_VERSION;
  }

  @Override
  public String getTenantIdFieldName() {
    return TENANT_ID;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        // Required process definition fields
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        // Incident specific fields
        .properties(INCIDENT_ID, p -> p.keyword(k -> k))
        .properties(CREATE_TIME, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(END_TIME, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(DURATION_IN_MS, p -> p.long_(k -> k))
        .properties(INCIDENT_TYPE, p -> p.keyword(k -> k))
        .properties(ACTIVITY_ID, p -> p.keyword(k -> k))
        .properties(FAILED_ACTIVITY_ID, p -> p.keyword(k -> k))
        .properties(INCIDENT_MESSAGE, p -> p.text(k -> k.index(true)))
        .properties(INCIDENT_STATUS, p -> p.keyword(k -> k))
        .properties(DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(PARTITION, p -> p.integer(k -> k));
  }

  protected String getIndexPrefix() {
    return FLAT_INCIDENT_INDEX_PREFIX;
  }
}
