/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.db.se.config.PluginConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;

public class ElasticsearchExporterConfiguration {

  private static final String DEFAULT_URL = "http://localhost:9200";

  /** Comma-separated Elasticsearch http urls */
  public String url = DEFAULT_URL;

  /** The request timeout for the elastic search client. The timeout unit is milliseconds. */
  public int requestTimeoutMs = 30_000;

  public final IndexConfiguration index = new IndexConfiguration();
  public final BulkConfiguration bulk = new BulkConfiguration();
  public final RetentionConfiguration retention = new RetentionConfiguration();
  public final List<PluginConfiguration> interceptorPlugins = new ArrayList<>();
  private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();

  public boolean hasAuthenticationPresent() {
    return getAuthentication().isPresent();
  }

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public List<PluginConfiguration> getInterceptorPlugins() {
    return interceptorPlugins;
  }

  @Override
  public String toString() {
    return "ElasticsearchExporterConfiguration{"
        + "url='"
        + url
        + '\''
        + ", requestTimeoutMs="
        + requestTimeoutMs
        + ", index="
        + index
        + ", bulk="
        + bulk
        + ", retention="
        + retention
        + ", authentication="
        + authentication
        + ", interceptors="
        + interceptorPlugins
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
      case MESSAGE_BATCH:
        return index.messageBatch;
      case MESSAGE_SUBSCRIPTION:
        return index.messageSubscription;
      case VARIABLE:
        return index.variable;
      case VARIABLE_DOCUMENT:
        return index.variableDocument;
      case PROCESS_INSTANCE:
        return index.processInstance;
      case PROCESS_INSTANCE_BATCH:
        return index.processInstanceBatch;
      case PROCESS_INSTANCE_CREATION:
        return index.processInstanceCreation;
      case PROCESS_INSTANCE_MIGRATION:
        return index.processInstanceMigration;
      case PROCESS_INSTANCE_MODIFICATION:
        return index.processInstanceModification;
      case PROCESS_MESSAGE_SUBSCRIPTION:
        return index.processMessageSubscription;
      case DECISION_REQUIREMENTS:
        return index.decisionRequirements;
      case DECISION:
        return index.decision;
      case DECISION_EVALUATION:
        return index.decisionEvaluation;
      case CHECKPOINT:
        return index.checkpoint;
      case TIMER:
        return index.timer;
      case MESSAGE_START_EVENT_SUBSCRIPTION:
        return index.messageStartEventSubscription;
      case PROCESS_EVENT:
        return index.processEvent;
      case DEPLOYMENT_DISTRIBUTION:
        return index.deploymentDistribution;
      case ESCALATION:
        return index.escalation;
      case SIGNAL:
        return index.signal;
      case SIGNAL_SUBSCRIPTION:
        return index.signalSubscription;
      case RESOURCE_DELETION:
        return index.resourceDeletion;
      case COMMAND_DISTRIBUTION:
        return index.commandDistribution;
      case FORM:
        return index.form;
      case USER_TASK:
        return index.userTask;
      case COMPENSATION_SUBSCRIPTION:
        return index.compensationSubscription;
      case MESSAGE_CORRELATION:
        return index.messageCorrelation;
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

    /**
     * Suffix for indices. Pattern is used together with the current date, to create a suffix for
     * the index. Useful to define whether an index should be created per month, day or even hour.
     *
     * <p>Example: yyyy-MM-dd -> 2023-12-03
     *
     * @see java.time.format.DateTimeFormatter
     */
    public String indexSuffixDatePattern = "yyyy-MM-dd";

    // update index template on startup
    public boolean createTemplate = true;

    // record types to export
    public boolean command = false;
    public boolean event = true;
    public boolean rejection = false;

    // value types to export
    public boolean decision = true;
    public boolean decisionEvaluation = true;
    public boolean decisionRequirements = true;
    public boolean deployment = true;
    public boolean error = true;
    public boolean incident = true;
    public boolean job = true;
    public boolean jobBatch = false;
    public boolean message = true;
    public boolean messageBatch = false;
    public boolean messageSubscription = true;
    public boolean process = true;
    public boolean processInstance = true;
    public boolean processInstanceBatch = false;
    public boolean processInstanceCreation = true;
    public boolean processInstanceMigration = true;
    public boolean processInstanceModification = true;
    public boolean processMessageSubscription = true;
    public boolean variable = true;
    public boolean variableDocument = true;

    public boolean checkpoint = false;
    public boolean timer = true;
    public boolean messageStartEventSubscription = true;
    public boolean processEvent = false;
    public boolean deploymentDistribution = true;
    public boolean escalation = true;
    public boolean signal = true;
    public boolean signalSubscription = true;
    public boolean resourceDeletion = true;
    public boolean commandDistribution = true;
    public boolean form = true;
    public boolean userTask = true;
    public boolean compensationSubscription = true;
    public boolean messageCorrelation = true;
    public boolean user = true;
    public boolean authorization = true;

    // index settings
    private Integer numberOfShards = null;
    private Integer numberOfReplicas = null;

    public Integer getNumberOfShards() {
      return numberOfShards;
    }

    public void setNumberOfShards(final Integer numberOfShards) {
      this.numberOfShards = numberOfShards;
    }

    public Integer getNumberOfReplicas() {
      return numberOfReplicas;
    }

    public void setNumberOfReplicas(final Integer numberOfReplicas) {
      this.numberOfReplicas = numberOfReplicas;
    }

    @Override
    public String toString() {
      return "IndexConfiguration{"
          + "prefix='"
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
          + ", decision="
          + decision
          + ", decisionEvaluation="
          + decisionEvaluation
          + ", decisionRequirements="
          + decisionRequirements
          + ", deployment="
          + deployment
          + ", error="
          + error
          + ", incident="
          + incident
          + ", job="
          + job
          + ", jobBatch="
          + jobBatch
          + ", message="
          + message
          + ", messageBatch="
          + messageBatch
          + ", messageSubscription="
          + messageSubscription
          + ", process="
          + process
          + ", processInstance="
          + processInstance
          + ", processInstanceBatch="
          + processInstanceBatch
          + ", processInstanceCreation="
          + processInstanceCreation
          + ", processInstanceMigration="
          + processInstanceMigration
          + ", processInstanceModification="
          + processInstanceModification
          + ", processMessageSubscription="
          + processMessageSubscription
          + ", variable="
          + variable
          + ", variableDocument="
          + variableDocument
          + ", checkpoint="
          + checkpoint
          + ", timer="
          + timer
          + ", messageStartEventSubscription="
          + messageStartEventSubscription
          + ", processEvent="
          + processEvent
          + ", deploymentDistribution="
          + deploymentDistribution
          + ", escalation="
          + escalation
          + ", signal="
          + signal
          + ", signalSubscription="
          + signalSubscription
          + ", resourceDeletion="
          + resourceDeletion
          + ", commandDistribution="
          + commandDistribution
          + ", form="
          + form
          + ", userTask="
          + userTask
          + ", compensationSubscription="
          + compensationSubscription
          + ", messageCorrelation="
          + messageCorrelation
          + ", user="
          + user
          + ", authorization="
          + authorization
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

  public static class RetentionConfiguration {

    private boolean enabled = false;
    private String minimumAge = "30d";
    private String policyName = "zeebe-record-retention-policy";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public String getMinimumAge() {
      return minimumAge;
    }

    public void setMinimumAge(final String minimumAge) {
      this.minimumAge = minimumAge;
    }

    public String getPolicyName() {
      return policyName;
    }

    public void setPolicyName(final String policyName) {
      this.policyName = policyName;
    }

    @Override
    public String toString() {
      return "RetentionConfiguration{"
          + "isEnabled="
          + enabled
          + ", minimumAge="
          + minimumAge
          + ", policyName='"
          + policyName
          + '\''
          + '}';
    }
  }
}
