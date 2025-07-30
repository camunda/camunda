/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;

public class OpensearchExporterConfiguration {

  private static final String DEFAULT_URL = "http://localhost:9200";

  /** Comma-separated Opensearch http urls */
  public String url = DEFAULT_URL;

  /** The request timeout for the opensearch client. The timeout unit is milliseconds. */
  public int requestTimeoutMs = 30_000;

  public final IndexConfiguration index = new IndexConfiguration();
  public final BulkConfiguration bulk = new BulkConfiguration();
  public final AwsConfiguration aws = new AwsConfiguration();
  public final RetentionConfiguration retention = new RetentionConfiguration();
  public final List<PluginConfiguration> interceptorPlugins = new ArrayList<>();
  private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();
  private boolean includeEnabledRecords = false;

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
    return "OpensearchExporterConfiguration{"
        + "url='"
        + url
        + '\''
        + ", index="
        + index
        + ", bulk="
        + bulk
        + ", aws="
        + aws
        + ", retention="
        + retention
        + ", interceptorPlugins="
        + interceptorPlugins
        + '}';
  }

  public boolean shouldIndexRecord(final Record<?> record) {
    return shouldIndexRecordType(record.getRecordType())
        && shouldIndexValueType(record.getValueType());
  }

  public boolean shouldIndexValueType(final ValueType valueType) {
    return switch (valueType) {
      case DEPLOYMENT -> index.deployment;
      case PROCESS -> index.process;
      case ERROR -> index.error;
      case INCIDENT -> index.incident;
      case JOB -> index.job;
      case JOB_BATCH -> index.jobBatch;
      case MESSAGE -> index.message;
      case MESSAGE_BATCH -> index.messageBatch;
      case MESSAGE_SUBSCRIPTION -> index.messageSubscription;
      case VARIABLE -> index.variable;
      case VARIABLE_DOCUMENT -> index.variableDocument;
      case PROCESS_INSTANCE -> index.processInstance;
      case PROCESS_INSTANCE_BATCH -> index.processInstanceBatch;
      case PROCESS_INSTANCE_CREATION -> index.processInstanceCreation;
      case PROCESS_INSTANCE_MIGRATION -> index.processInstanceMigration;
      case PROCESS_INSTANCE_MODIFICATION -> index.processInstanceModification;
      case PROCESS_MESSAGE_SUBSCRIPTION -> index.processMessageSubscription;
      case DECISION_REQUIREMENTS -> index.decisionRequirements;
      case DECISION -> index.decision;
      case DECISION_EVALUATION -> index.decisionEvaluation;
      case CHECKPOINT -> index.checkpoint;
      case TIMER -> index.timer;
      case MESSAGE_START_EVENT_SUBSCRIPTION -> index.messageStartEventSubscription;
      case PROCESS_EVENT -> index.processEvent;
      case DEPLOYMENT_DISTRIBUTION -> index.deploymentDistribution;
      case ESCALATION -> index.escalation;
      case SIGNAL -> index.signal;
      case SIGNAL_SUBSCRIPTION -> index.signalSubscription;
      case RESOURCE_DELETION -> index.resourceDeletion;
      case COMMAND_DISTRIBUTION -> index.commandDistribution;
      case FORM -> index.form;
      case USER_TASK -> index.userTask;
      case COMPENSATION_SUBSCRIPTION -> index.compensationSubscription;
      case MESSAGE_CORRELATION -> index.messageCorrelation;
      case AD_HOC_SUB_PROCESS_INSTRUCTION -> index.adHocSubProcessInstruction;
      case ASYNC_REQUEST -> index.asyncRequest;
      case RUNTIME_INSTRUCTION -> index.runtimeInstruction;
      default -> false;
    };
  }

  /**
   * Not all value records are required to be exported from 8.8 onward. The following included
   * records are required by Optimize and Zeebe-Analytics so they must continue to be exported by
   * the {@link OpensearchExporter}:
   *
   * @param valueType the value type of the record
   * @return true if the record should be indexed, false otherwise
   */
  public boolean shouldIndexRequiredValueType(final ValueType valueType) {
    return switch (valueType) {
      case DEPLOYMENT -> index.deployment;
      case PROCESS -> index.process;
      case INCIDENT -> index.incident;
      case VARIABLE -> index.variable;
      case PROCESS_INSTANCE -> index.processInstance;
      case USER_TASK -> index.userTask;
      case JOB -> index.job;
      default -> false;
    };
  }

  public boolean shouldIndexRecordType(final RecordType recordType) {
    return switch (recordType) {
      case EVENT -> index.event;
      case COMMAND -> index.command;
      case COMMAND_REJECTION -> index.rejection;
      default -> false;
    };
  }

  public boolean getIsIncludeEnabledRecords() {
    return includeEnabledRecords;
  }

  public void setIncludeEnabledRecords(final boolean includeEnabledRecords) {
    this.includeEnabledRecords = includeEnabledRecords;
  }

  public static class IndexConfiguration {

    public static final int DEFAULT_INDEX_TEMPLATE_PRIORITY = 20;
    // prefix for index and templates
    public String prefix = "zeebe-record";

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
    public boolean adHocSubProcessInstruction = true;

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

    public boolean runtimeInstruction = true;

    public boolean batchOperationCreation = false;
    public boolean batchOperationChunk = false;
    public boolean batchOperationExecution = false;
    public boolean batchOperationLifecycleManagement = false;
    public boolean batchOperationPartitionLifecycle = false;
    public boolean batchOperationInitialization = false;

    public boolean asyncRequest = false;

    public boolean usageMetrics = false;

    // index settings
    private Integer numberOfShards = null;
    private Integer numberOfReplicas = null;
    private int priority = DEFAULT_INDEX_TEMPLATE_PRIORITY;

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

    public int getPriority() {
      return priority;
    }

    public void setPriority(final int priority) {
      this.priority = priority;
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
          + ", messageBatch="
          + messageBatch
          + ", messageSubscription="
          + messageSubscription
          + ", variable="
          + variable
          + ", variableDocument="
          + variableDocument
          + ", processInstance="
          + processInstanceBatch
          + ", processInstanceBatch="
          + processInstance
          + ", processInstanceCreation="
          + processInstanceCreation
          + ", processInstanceMigration="
          + processInstanceMigration
          + ", processInstanceModification="
          + processInstanceModification
          + ", processMessageSubscription="
          + processMessageSubscription
          + ", decisionRequirements="
          + decisionRequirements
          + ", decision="
          + decision
          + ", decisionEvaluation="
          + decisionEvaluation
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
          + ", adHocSubProcessInstruction="
          + adHocSubProcessInstruction
          + ", recordDistribution="
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
          + ", asyncRequest="
          + asyncRequest
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

  public static class AwsConfiguration {

    private static final String AWS_REGION_ENV_VARIABLE = "AWS_REGION";
    private static final String AWS_OPENSEARCH_SERVICE_NAME = "es";

    public boolean enabled = false;
    // The service name is defined by AWS. This is currently "es" and is not likely to change.
    // If it does change this configuration can still be overridden.
    public String serviceName = AWS_OPENSEARCH_SERVICE_NAME;
    // The AWS_REGION gets injected into the pod by AWS. If we are running on AWS this should always
    // be available.
    public String region = System.getenv(AWS_REGION_ENV_VARIABLE);

    @Override
    public String toString() {
      if (enabled) {
        return "AwsConfiguration{serviceName=" + serviceName + ", region=" + region + '}';
      } else {
        return "AwsConfiguration{Disabled}";
      }
    }
  }

  public static class RetentionConfiguration {
    private boolean enabled = false;
    private String minimumAge = "30d";
    private String policyName = "zeebe-record-retention-policy";
    private String policyDescription = "Zeebe record retention policy";

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

    public String getPolicyDescription() {
      return policyDescription;
    }

    public void setPolicyDescription(final String policyDescription) {
      this.policyDescription = policyDescription;
    }

    @Override
    public String toString() {
      return "RetentionConfiguration{"
          + "isEnabled="
          + enabled
          + ", minimumAge='"
          + minimumAge
          + ", policyName='"
          + policyName
          + ", policyDescription='"
          + policyDescription
          + '\''
          + '}';
    }
  }
}
