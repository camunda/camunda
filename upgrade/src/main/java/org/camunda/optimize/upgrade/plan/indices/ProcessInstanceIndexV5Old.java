/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.indices;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.DefinitionBasedType;
import org.camunda.optimize.service.es.schema.index.InstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TIMESTAMP;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ASSIGNEE_OPERATION_USER_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_GROUP_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_TIMESTAMP;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.CANDIDATE_GROUP_OPERATION_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_CREATE_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_DURATION_IN_MS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_END_TIME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_FAILED_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_INCIDENT_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_MESSAGE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENT_STATUS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID_FOR_ACTIVITY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ASSIGNEE_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUPS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CANDIDATE_GROUP_OPERATIONS;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DELETE_REASON;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VERSION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_SHARDS_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstanceIndexV5Old extends DefaultIndexMappingCreator implements DefinitionBasedType, InstanceType {

  public static final int VERSION = 5;

  @Setter
  private String indexName = "process-instance";

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
  public XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    // @formatter:off
    XContentBuilder newBuilder =  builder
      .startObject(PROCESS_DEFINITION_KEY)
       .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(BUSINESS_KEY)
       .field("type", "keyword")
      .endObject()
      .startObject(START_DATE)
       .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(DURATION)
        .field("type", "long")
      .endObject()
      .startObject(ENGINE)
       .field("type", "keyword")
      .endObject()
      .startObject(TENANT_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(STATE)
       .field("type", "keyword")
      .endObject()
      .startObject(EVENTS)
        .field("type", "nested")
      .startObject("properties");

    addNestedEventField(newBuilder)
      .endObject()
      .endObject()
      .startObject(USER_TASKS)
       .field("type", "nested")
      .startObject("properties");

    addNestedUserTaskField(newBuilder)
      .endObject()
      .endObject()
      .startObject(VARIABLES)
       .field("type", "nested")
      .startObject("properties");

    addNestedVariableField(newBuilder)
      .endObject()
      .endObject()
      .startObject(INCIDENTS)
       .field("type", "nested")
      .startObject("properties");

    addNestedIncidentField(newBuilder)
      .endObject()
      .endObject();
    return newBuilder;
    // @formatter:on
  }

  private XContentBuilder addNestedEventField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(EVENT_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_ID_FOR_ACTIVITY)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(ACTIVITY_START_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(ACTIVITY_END_DATE)
       .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(ACTIVITY_CANCELED)
        .field("type", "boolean")
      .endObject();
    // @formatter:on
  }

  private XContentBuilder addNestedVariableField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(VARIABLE_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_NAME)
       .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field("type", "keyword")
      .startObject(FIELDS);

    addValueMultifields(builder)
      .endObject()
      .endObject()
      .startObject(VARIABLE_VERSION)
        .field("type", "long")
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedUserTaskField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(USER_TASK_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ACTIVITY_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ACTIVITY_INSTANCE_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_TOTAL_DURATION)
       .field("type", "long")
      .endObject()
      .startObject(USER_TASK_IDLE_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(USER_TASK_WORK_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(USER_TASK_START_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_CLAIM_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_DUE_DATE)
        .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_DELETE_REASON)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_CANCELED)
       .field("type", "boolean")
      .endObject()
      .startObject(USER_TASK_ASSIGNEE)
       .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUPS)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ASSIGNEE_OPERATIONS)
        .field("type", "nested")
      .startObject("properties");

    addNestedAssigneeOperations(builder)
      .endObject()
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUP_OPERATIONS)
       .field("type", "nested")
       .startObject("properties");

    addNestedCandidateGroupOperations(builder)
      .endObject()
      .endObject();
    // @formatter:on
    return builder;
  }

  private XContentBuilder addNestedAssigneeOperations(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(ASSIGNEE_OPERATION_ID)
       .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_USER_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TIMESTAMP)
       .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
    ;
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedCandidateGroupOperations(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(CANDIDATE_GROUP_OPERATION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_GROUP_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TIMESTAMP)
       .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
    ;
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedIncidentField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(INCIDENT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_CREATE_TIME)
       .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_END_TIME)
       .field("type", "date")
       .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_DURATION_IN_MS)
        .field("type", "long")
      .endObject()
      .startObject(INCIDENT_INCIDENT_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_FAILED_ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_MESSAGE)
        .field("type", "text")
        .field("index", true)
      .endObject()
      .startObject(INCIDENT_STATUS)
       .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, configurationService.getEsNumberOfShards());
  }

}
