/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

/**
 * Template descriptor for the {@code agent-history} index. Implements {@link
 * ProcessInstanceDependant} so the archiver can sweep history documents when the parent root
 * process instance is archived, and {@link Prio4Backup} because history entries are detail records
 * that depend on their parent {@code AgentInstance} (Prio3) and {@code ProcessInstance} (Prio2)
 * being restored first.
 */
public class AgentHistoryTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  public static final String INDEX_NAME = "agent-history";
  public static final String INDEX_VERSION = "8.10.0";

  public static final String KEY = "key";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String TENANT_ID = "tenantId";
  public static final String JOB_KEY = "jobKey";
  public static final String JOB_LEASE = "jobLease";
  public static final String ITERATION = "iteration";
  public static final String ROLE = "role";
  public static final String COMMIT_STATUS = "commitStatus";
  public static final String PRODUCED_AT = "producedAt";

  public AgentHistoryTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }
}
