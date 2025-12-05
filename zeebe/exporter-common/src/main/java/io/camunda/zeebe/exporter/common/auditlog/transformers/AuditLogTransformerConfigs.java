/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent.MODIFIED;

import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;

public class AuditLogTransformerConfigs {
  public static final TransformerConfig PROCESS_INSTANCE_MODIFICATION_CONFIG =
      TransformerConfig.with(PROCESS_INSTANCE_MODIFICATION).withIntents(MODIFIED);
}
