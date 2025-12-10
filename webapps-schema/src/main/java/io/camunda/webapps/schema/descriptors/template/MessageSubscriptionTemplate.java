/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

public class MessageSubscriptionTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  // The name of the index does not align with the class name for historical reasons.
  public static final String INDEX_NAME = "event";

  public static final String ID = "id";
  public static final String KEY = "key";

  public static final String PROCESS_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";

  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";

  public static final String DATE_TIME = "dateTime";

  public static final String MESSAGE_NAME = "messageName";
  public static final String CORRELATION_KEY = "correlationKey";
  public static final String POSITION_MESSAGE = "positionProcessMessageSubscription";
  public static final String MESSAGE_SUBSCRIPTION_STATE = "eventType";

  public static final String METADATA = "metadata";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String EVENT_SOURCE_TYPE = "eventSourceType";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_TYPE = "jobType";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_RETRIES = "jobRetries";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_WORKER = "jobWorker";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_DEADLINE = "jobDeadline";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_CUSTOM_HEADERS = "jobCustomHeaders";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String JOB_KEY = "jobKey";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String INCIDENT_ERROR_TYPE = "incidentErrorType";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String INCIDENT_ERROR_MSG = "incidentErrorMessage";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String POSITION_INCIDENT = "positionIncident";

  /**
   * @deprecated since 8.9. This field should not be used anymore as it only exists for historical
   *     reasons.
   */
  @Deprecated public static final String POSITION_JOB = "positionJob";

  public static final String ROOT_PROCESS_INSTANCE_KEY = "rootProcessInstanceKey";

  public MessageSubscriptionTemplate(final String indexPrefix, final boolean isElasticsearch) {
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
    return "8.3.0";
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
