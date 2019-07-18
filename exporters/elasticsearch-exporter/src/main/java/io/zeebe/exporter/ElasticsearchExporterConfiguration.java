/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;

public class ElasticsearchExporterConfiguration {

  // elasticsearch http url
  public String url = "http://localhost:9200";

  public IndexConfiguration index = new IndexConfiguration();
  public BulkConfiguration bulk = new BulkConfiguration();
  public AuthenticationConfiguration authentication = new AuthenticationConfiguration();

  @Override
  public String toString() {
    return "ElasticsearchExporterConfiguration{"
        + "url='"
        + url
        + '\''
        + ", index="
        + index
        + ", bulk="
        + bulk
        + ", authentication="
        + authentication
        + '}';
  }

  public boolean shouldIndexRecord(Record<?> record) {
    return shouldIndexRecordType(record.getRecordType())
        && shouldIndexValueType(record.getValueType());
  }

  public boolean shouldIndexValueType(ValueType valueType) {
    switch (valueType) {
      case DEPLOYMENT:
        return index.deployment;
      case ERROR:
        return index.error;
      case INCIDENT:
        return index.incident;
      case JOB:
        return index.job;
      case JOB_BATCH:
        return index.jobBatch;
      case MESSAGE:
        return index.message;
      case MESSAGE_SUBSCRIPTION:
        return index.messageSubscription;
      case VARIABLE:
        return index.variable;
      case VARIABLE_DOCUMENT:
        return index.variableDocument;
      case WORKFLOW_INSTANCE:
        return index.workflowInstance;
      case WORKFLOW_INSTANCE_CREATION:
        return index.workflowInstanceCreation;
      case WORKFLOW_INSTANCE_SUBSCRIPTION:
        return index.workflowInstanceSubscription;
      default:
        return false;
    }
  }

  public boolean shouldIndexRecordType(RecordType recordType) {
    switch (recordType) {
      case EVENT:
        return index.event;
      case COMMAND:
        return index.command;
      case COMMAND_REJECTION:
        return index.rejection;
      default:
        return false;
    }
  }

  public static class IndexConfiguration {
    // prefix for index and templates
    public String prefix = "zeebe-record";

    // update index template on startup
    public boolean createTemplate = true;

    // record types to export
    public boolean command = false;
    public boolean event = true;
    public boolean rejection = false;

    // value types to export
    public boolean deployment = true;
    public boolean error = true;
    public boolean incident = true;
    public boolean job = true;
    public boolean jobBatch = false;
    public boolean message = false;
    public boolean messageSubscription = false;
    public boolean variable = true;
    public boolean variableDocument = false;
    public boolean workflowInstance = true;
    public boolean workflowInstanceCreation = false;
    public boolean workflowInstanceSubscription = false;

    @Override
    public String toString() {
      return "IndexConfiguration{"
          + "indexPrefix='"
          + prefix
          + '\''
          + ", createTemplate="
          + createTemplate
          + ", command="
          + command
          + ", event="
          + event
          + ", rejection="
          + rejection
          + ", error="
          + error
          + ", deployment="
          + deployment
          + ", incident="
          + incident
          + ", job="
          + job
          + ", message="
          + message
          + ", messageSubscription="
          + messageSubscription
          + ", variable="
          + variable
          + ", variableDocument="
          + variableDocument
          + ", workflowInstance="
          + workflowInstance
          + ", workflowInstanceCreation="
          + workflowInstanceCreation
          + ", workflowInstanceSubscription="
          + workflowInstanceSubscription
          + '}';
    }
  }

  public static class BulkConfiguration {
    // delay before forced flush
    public int delay = 5;
    // bulk size before flush
    public int size = 1_000;

    @Override
    public String toString() {
      return "BulkConfiguration{" + "delay=" + delay + ", size=" + size + '}';
    }
  }

  public static class AuthenticationConfiguration {
    public String username;
    public String password;

    public boolean isPresent() {
      return (username != null && !username.isEmpty()) && (password != null && !password.isEmpty());
    }

    @Override
    public String toString() {
      return "AuthenticationConfiguration{" + "username='" + username + '\'' + '}';
    }
  }
}
