/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;

public class ElasticsearchExporterConfiguration {

  private static final String DEFAULT_URL = "http://localhost:9200";

  /** Comma-separated Elasticsearch http urls */
  public String url = DEFAULT_URL;

  /** The request timeout for the elastic search client. The timeout unit is milliseconds. */
  public int requestTimeoutMs = 30_000;

  public final IndexConfiguration index = new IndexConfiguration();
  public final BulkConfiguration bulk = new BulkConfiguration();
  private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();

  public boolean hasAuthenticationPresent() {
    return getAuthentication().isPresent();
  }

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

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
        + '}';
  }

  public boolean shouldIndexRecord(final Record<?> record) {
    return shouldIndexRecordType(record.getRecordType())
        && shouldIndexValueType(record.getValueType());
  }

  public boolean shouldIndexValueType(final ValueType valueType) {
    switch (valueType) {
      case DEPLOYMENT:
        return index.deployment;
      case PROCESS:
        return index.process;
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
      case PROCESS_INSTANCE:
        return index.processInstance;
      case PROCESS_INSTANCE_CREATION:
        return index.processInstanceCreation;
      case PROCESS_MESSAGE_SUBSCRIPTION:
        return index.processMessageSubscription;
      default:
        return false;
    }
  }

  public boolean shouldIndexRecordType(final RecordType recordType) {
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
    public boolean deployment = false;
    public boolean process = true;
    public boolean error = true;
    public boolean incident = true;
    public boolean job = true;
    public boolean jobBatch = false;
    public boolean message = false;
    public boolean messageSubscription = false;
    public boolean variable = true;
    public boolean variableDocument = true;
    public boolean processInstance = true;
    public boolean processInstanceCreation = false;
    public boolean processMessageSubscription = false;

    // index settings
    private Integer numberOfShards = null;
    private Integer numberOfReplicas = null;

    public Integer getNumberOfShards() {
      return numberOfShards;
    }

    public void setNumberOfShards(Integer numberOfShards) {
      this.numberOfShards = numberOfShards;
    }

    public Integer getNumberOfReplicas() {
      return numberOfReplicas;
    }

    public void setNumberOfReplicas(Integer numberOfReplicas) {
      this.numberOfReplicas = numberOfReplicas;
    }

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
          + ", process="
          + process
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
          + ", processInstance="
          + processInstance
          + ", processInstanceCreation="
          + processInstanceCreation
          + ", processMessageSubscription="
          + processMessageSubscription
          + '}';
    }
  }

  public static class BulkConfiguration {
    // delay before forced flush
    public int delay = 5;
    // bulk size before flush
    public int size = 1_000;
    // memory limit of the bulk in bytes before flush
    public int memoryLimit = 10 * 1024 * 1024;

    @Override
    public String toString() {
      return "BulkConfiguration{"
          + "delay="
          + delay
          + ", size="
          + size
          + ", memoryLimit="
          + memoryLimit
          + '}';
    }
  }

  public static class AuthenticationConfiguration {
    private String username;
    private String password;

    public boolean isPresent() {
      return (username != null && !username.isEmpty()) && (password != null && !password.isEmpty());
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(final String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(final String password) {
      this.password = password;
    }

    @Override
    public String toString() {
      // we don't want to expose this information
      return "AuthenticationConfiguration{Confidential information}";
    }
  }
}
