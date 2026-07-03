/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * A deployed process definition, derived from a {@code PROCESS} record's resource. Unlike the
 * execution facts this is a dimension, not a measure: it carries the BPMN XML so the dashboard can
 * render the model and overlay a heatmap of the per-element metrics onto it. Upserted by key, so a
 * redelivered deployment is idempotent.
 */
public record ProcessDefinitionFact(
    String bpmnProcessId, long processDefinitionKey, int version, String tenantId, String bpmnXml)
    implements ProcessExecutionFact {}
