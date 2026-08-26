/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Per-index primary shard counts for the document-based secondary storage.
 *
 * <p>One nullable field per secondary-storage index. A {@code null} field means "not configured":
 * the schema manager then falls back to the index descriptor's own default and, failing that, to
 * the global {@code number-of-shards} knob.
 *
 * <p>This replaces the former {@code Map<String, Integer>} keyed by raw index name. The raw names
 * contain dashes, which environment variables cannot express, so {@code post-importer-queue} and
 * friends were unreachable outside YAML. Fields keep the kebab-case YAML keys working through
 * Spring's relaxed binding ({@code list-view} binds to {@code listView}) while also making {@code
 * CAMUNDA_..._NUMBEROFSHARDSPERINDEX_LISTVIEW} bind, and make the valid keys discoverable from the
 * type.
 *
 * <p>Field names mirror the {@code INDEX_NAME} constants on the descriptors in {@code
 * io.camunda.webapps.schema.descriptors}. Adding an index there means adding a field here,
 * otherwise that index cannot be overridden.
 */
@NullMarked
public class NumberOfShardsPerIndex {

  /*
   * Plain indices. These hold configuration, definition or singleton data and default to a
   * single primary shard; setting a field here is the escape hatch for the rare deployment that
   * outgrows one shard.
   */

  /** Primary shards for the agent definition index (agent-definition). */
  private @Nullable Integer agentDefinition;

  /** Primary shards for the audit log cleanup index (audit-log-cleanup). */
  private @Nullable Integer auditLogCleanup;

  /** Primary shards for the authorization index (authorization). */
  private @Nullable Integer authorization;

  /** Primary shards for the cluster variable index (cluster-variable). */
  private @Nullable Integer clusterVariable;

  /** Primary shards for the decision definition index (decision). */
  private @Nullable Integer decision;

  /** Primary shards for the decision requirements index (decision-requirements). */
  private @Nullable Integer decisionRequirements;

  /** Primary shards for the deployed resource index (deployed-resource). */
  private @Nullable Integer deployedResource;

  /** Primary shards for the form index (form). */
  private @Nullable Integer form;

  /** Primary shards for the global listener index (global-listener). */
  private @Nullable Integer globalListener;

  /** Primary shards for the group index (group). */
  private @Nullable Integer group;

  /** Primary shards for the history deletion index (history-deletion). */
  private @Nullable Integer historyDeletion;

  /** Primary shards for the mapping rule index (mapping-rule). */
  private @Nullable Integer mappingRule;

  /** Primary shards for the schema metadata index (metadata). */
  private @Nullable Integer metadata;

  /** Primary shards for the process definition index (process). */
  private @Nullable Integer process;

  /** Primary shards for the role index (role). */
  private @Nullable Integer role;

  /** Primary shards for the tenant index (tenant). */
  private @Nullable Integer tenant;

  /** Primary shards for the user index (user). */
  private @Nullable Integer user;

  /** Primary shards for the persistent web session index (web-session). */
  private @Nullable Integer webSession;

  /*
   * Template-backed indices. These carry process-instance volume and follow the global
   * number-of-shards knob unless pinned by their descriptor or overridden here.
   */

  /** Primary shards for the agent history index (agent-history). */
  private @Nullable Integer agentHistory;

  /** Primary shards for the agent instance index (agent-instance). */
  private @Nullable Integer agentInstance;

  /** Primary shards for the audit log index (audit-log). */
  private @Nullable Integer auditLog;

  /** Primary shards for the batch operation index (batch-operation). */
  private @Nullable Integer batchOperation;

  /**
   * Primary shards for the correlated message subscription index (correlated-message-subscription).
   */
  private @Nullable Integer correlatedMessageSubscription;

  /** Primary shards for the decision instance index (decision-instance). */
  private @Nullable Integer decisionInstance;

  /** Primary shards for the draft task variable index (draft-task-variable). */
  private @Nullable Integer draftTaskVariable;

  /** Primary shards for the message subscription index (event). */
  private @Nullable Integer event;

  /** Primary shards for the flow node instance index (flownode-instance). */
  private @Nullable Integer flownodeInstance;

  /** Primary shards for the incident index (incident). */
  private @Nullable Integer incident;

  /** Primary shards for the job index (job). */
  private @Nullable Integer job;

  /** Primary shards for the job metrics batch index (job-metrics-batch). */
  private @Nullable Integer jobMetricsBatch;

  /** Primary shards for the process instance list view index (list-view). */
  private @Nullable Integer listView;

  /** Primary shards for the message index (message). */
  private @Nullable Integer message;

  /** Primary shards for the operation index (operation). */
  private @Nullable Integer operation;

  /** Primary shards for the post importer queue index (post-importer-queue). */
  private @Nullable Integer postImporterQueue;

  /** Primary shards for the sequence flow index (sequence-flow). */
  private @Nullable Integer sequenceFlow;

  /** Primary shards for the user task index (task). */
  private @Nullable Integer task;

  /** Primary shards for the task variable snapshot index (task-variable). */
  private @Nullable Integer taskVariable;

  /** Primary shards for the usage metric index (usage-metric). */
  private @Nullable Integer usageMetric;

  /** Primary shards for the usage metric task user index (usage-metric-tu). */
  private @Nullable Integer usageMetricTu;

  /** Primary shards for the variable index (variable). */
  private @Nullable Integer variable;

  /** Primary shards for the wait state index (wait-state). */
  private @Nullable Integer waitState;

  /**
   * Projects the configured fields onto the index-name keyed map the schema manager consumes. Unset
   * fields are left out, so an absent entry keeps the descriptor default in play.
   */
  public Map<String, Integer> toIndexNameMap() {
    final Map<String, Integer> shardsByIndexName = new LinkedHashMap<>();
    putIfSet(shardsByIndexName, "agent-definition", agentDefinition);
    putIfSet(shardsByIndexName, "audit-log-cleanup", auditLogCleanup);
    putIfSet(shardsByIndexName, "authorization", authorization);
    putIfSet(shardsByIndexName, "cluster-variable", clusterVariable);
    putIfSet(shardsByIndexName, "decision", decision);
    putIfSet(shardsByIndexName, "decision-requirements", decisionRequirements);
    putIfSet(shardsByIndexName, "deployed-resource", deployedResource);
    putIfSet(shardsByIndexName, "form", form);
    putIfSet(shardsByIndexName, "global-listener", globalListener);
    putIfSet(shardsByIndexName, "group", group);
    putIfSet(shardsByIndexName, "history-deletion", historyDeletion);
    putIfSet(shardsByIndexName, "mapping-rule", mappingRule);
    putIfSet(shardsByIndexName, "metadata", metadata);
    putIfSet(shardsByIndexName, "process", process);
    putIfSet(shardsByIndexName, "role", role);
    putIfSet(shardsByIndexName, "tenant", tenant);
    putIfSet(shardsByIndexName, "user", user);
    putIfSet(shardsByIndexName, "web-session", webSession);
    putIfSet(shardsByIndexName, "agent-history", agentHistory);
    putIfSet(shardsByIndexName, "agent-instance", agentInstance);
    putIfSet(shardsByIndexName, "audit-log", auditLog);
    putIfSet(shardsByIndexName, "batch-operation", batchOperation);
    putIfSet(shardsByIndexName, "correlated-message-subscription", correlatedMessageSubscription);
    putIfSet(shardsByIndexName, "decision-instance", decisionInstance);
    putIfSet(shardsByIndexName, "draft-task-variable", draftTaskVariable);
    putIfSet(shardsByIndexName, "event", event);
    putIfSet(shardsByIndexName, "flownode-instance", flownodeInstance);
    putIfSet(shardsByIndexName, "incident", incident);
    putIfSet(shardsByIndexName, "job", job);
    putIfSet(shardsByIndexName, "job-metrics-batch", jobMetricsBatch);
    putIfSet(shardsByIndexName, "list-view", listView);
    putIfSet(shardsByIndexName, "message", message);
    putIfSet(shardsByIndexName, "operation", operation);
    putIfSet(shardsByIndexName, "post-importer-queue", postImporterQueue);
    putIfSet(shardsByIndexName, "sequence-flow", sequenceFlow);
    putIfSet(shardsByIndexName, "task", task);
    putIfSet(shardsByIndexName, "task-variable", taskVariable);
    putIfSet(shardsByIndexName, "usage-metric", usageMetric);
    putIfSet(shardsByIndexName, "usage-metric-tu", usageMetricTu);
    putIfSet(shardsByIndexName, "variable", variable);
    putIfSet(shardsByIndexName, "wait-state", waitState);
    return shardsByIndexName;
  }

  private static void putIfSet(
      final Map<String, Integer> target, final String indexName, final @Nullable Integer shards) {
    if (shards != null) {
      target.put(indexName, shards);
    }
  }

  public @Nullable Integer getAgentDefinition() {
    return agentDefinition;
  }

  public void setAgentDefinition(final @Nullable Integer agentDefinition) {
    this.agentDefinition = agentDefinition;
  }

  public @Nullable Integer getAuditLogCleanup() {
    return auditLogCleanup;
  }

  public void setAuditLogCleanup(final @Nullable Integer auditLogCleanup) {
    this.auditLogCleanup = auditLogCleanup;
  }

  public @Nullable Integer getAuthorization() {
    return authorization;
  }

  public void setAuthorization(final @Nullable Integer authorization) {
    this.authorization = authorization;
  }

  public @Nullable Integer getClusterVariable() {
    return clusterVariable;
  }

  public void setClusterVariable(final @Nullable Integer clusterVariable) {
    this.clusterVariable = clusterVariable;
  }

  public @Nullable Integer getDecision() {
    return decision;
  }

  public void setDecision(final @Nullable Integer decision) {
    this.decision = decision;
  }

  public @Nullable Integer getDecisionRequirements() {
    return decisionRequirements;
  }

  public void setDecisionRequirements(final @Nullable Integer decisionRequirements) {
    this.decisionRequirements = decisionRequirements;
  }

  public @Nullable Integer getDeployedResource() {
    return deployedResource;
  }

  public void setDeployedResource(final @Nullable Integer deployedResource) {
    this.deployedResource = deployedResource;
  }

  public @Nullable Integer getForm() {
    return form;
  }

  public void setForm(final @Nullable Integer form) {
    this.form = form;
  }

  public @Nullable Integer getGlobalListener() {
    return globalListener;
  }

  public void setGlobalListener(final @Nullable Integer globalListener) {
    this.globalListener = globalListener;
  }

  public @Nullable Integer getGroup() {
    return group;
  }

  public void setGroup(final @Nullable Integer group) {
    this.group = group;
  }

  public @Nullable Integer getHistoryDeletion() {
    return historyDeletion;
  }

  public void setHistoryDeletion(final @Nullable Integer historyDeletion) {
    this.historyDeletion = historyDeletion;
  }

  public @Nullable Integer getMappingRule() {
    return mappingRule;
  }

  public void setMappingRule(final @Nullable Integer mappingRule) {
    this.mappingRule = mappingRule;
  }

  public @Nullable Integer getMetadata() {
    return metadata;
  }

  public void setMetadata(final @Nullable Integer metadata) {
    this.metadata = metadata;
  }

  public @Nullable Integer getProcess() {
    return process;
  }

  public void setProcess(final @Nullable Integer process) {
    this.process = process;
  }

  public @Nullable Integer getRole() {
    return role;
  }

  public void setRole(final @Nullable Integer role) {
    this.role = role;
  }

  public @Nullable Integer getTenant() {
    return tenant;
  }

  public void setTenant(final @Nullable Integer tenant) {
    this.tenant = tenant;
  }

  public @Nullable Integer getUser() {
    return user;
  }

  public void setUser(final @Nullable Integer user) {
    this.user = user;
  }

  public @Nullable Integer getWebSession() {
    return webSession;
  }

  public void setWebSession(final @Nullable Integer webSession) {
    this.webSession = webSession;
  }

  public @Nullable Integer getAgentHistory() {
    return agentHistory;
  }

  public void setAgentHistory(final @Nullable Integer agentHistory) {
    this.agentHistory = agentHistory;
  }

  public @Nullable Integer getAgentInstance() {
    return agentInstance;
  }

  public void setAgentInstance(final @Nullable Integer agentInstance) {
    this.agentInstance = agentInstance;
  }

  public @Nullable Integer getAuditLog() {
    return auditLog;
  }

  public void setAuditLog(final @Nullable Integer auditLog) {
    this.auditLog = auditLog;
  }

  public @Nullable Integer getBatchOperation() {
    return batchOperation;
  }

  public void setBatchOperation(final @Nullable Integer batchOperation) {
    this.batchOperation = batchOperation;
  }

  public @Nullable Integer getCorrelatedMessageSubscription() {
    return correlatedMessageSubscription;
  }

  public void setCorrelatedMessageSubscription(
      final @Nullable Integer correlatedMessageSubscription) {
    this.correlatedMessageSubscription = correlatedMessageSubscription;
  }

  public @Nullable Integer getDecisionInstance() {
    return decisionInstance;
  }

  public void setDecisionInstance(final @Nullable Integer decisionInstance) {
    this.decisionInstance = decisionInstance;
  }

  public @Nullable Integer getDraftTaskVariable() {
    return draftTaskVariable;
  }

  public void setDraftTaskVariable(final @Nullable Integer draftTaskVariable) {
    this.draftTaskVariable = draftTaskVariable;
  }

  public @Nullable Integer getEvent() {
    return event;
  }

  public void setEvent(final @Nullable Integer event) {
    this.event = event;
  }

  public @Nullable Integer getFlownodeInstance() {
    return flownodeInstance;
  }

  public void setFlownodeInstance(final @Nullable Integer flownodeInstance) {
    this.flownodeInstance = flownodeInstance;
  }

  public @Nullable Integer getIncident() {
    return incident;
  }

  public void setIncident(final @Nullable Integer incident) {
    this.incident = incident;
  }

  public @Nullable Integer getJob() {
    return job;
  }

  public void setJob(final @Nullable Integer job) {
    this.job = job;
  }

  public @Nullable Integer getJobMetricsBatch() {
    return jobMetricsBatch;
  }

  public void setJobMetricsBatch(final @Nullable Integer jobMetricsBatch) {
    this.jobMetricsBatch = jobMetricsBatch;
  }

  public @Nullable Integer getListView() {
    return listView;
  }

  public void setListView(final @Nullable Integer listView) {
    this.listView = listView;
  }

  public @Nullable Integer getMessage() {
    return message;
  }

  public void setMessage(final @Nullable Integer message) {
    this.message = message;
  }

  public @Nullable Integer getOperation() {
    return operation;
  }

  public void setOperation(final @Nullable Integer operation) {
    this.operation = operation;
  }

  public @Nullable Integer getPostImporterQueue() {
    return postImporterQueue;
  }

  public void setPostImporterQueue(final @Nullable Integer postImporterQueue) {
    this.postImporterQueue = postImporterQueue;
  }

  public @Nullable Integer getSequenceFlow() {
    return sequenceFlow;
  }

  public void setSequenceFlow(final @Nullable Integer sequenceFlow) {
    this.sequenceFlow = sequenceFlow;
  }

  public @Nullable Integer getTask() {
    return task;
  }

  public void setTask(final @Nullable Integer task) {
    this.task = task;
  }

  public @Nullable Integer getTaskVariable() {
    return taskVariable;
  }

  public void setTaskVariable(final @Nullable Integer taskVariable) {
    this.taskVariable = taskVariable;
  }

  public @Nullable Integer getUsageMetric() {
    return usageMetric;
  }

  public void setUsageMetric(final @Nullable Integer usageMetric) {
    this.usageMetric = usageMetric;
  }

  public @Nullable Integer getUsageMetricTu() {
    return usageMetricTu;
  }

  public void setUsageMetricTu(final @Nullable Integer usageMetricTu) {
    this.usageMetricTu = usageMetricTu;
  }

  public @Nullable Integer getVariable() {
    return variable;
  }

  public void setVariable(final @Nullable Integer variable) {
    this.variable = variable;
  }

  public @Nullable Integer getWaitState() {
    return waitState;
  }

  public void setWaitState(final @Nullable Integer waitState) {
    this.waitState = waitState;
  }

  @Override
  public String toString() {
    return "NumberOfShardsPerIndex" + toIndexNameMap();
  }
}
