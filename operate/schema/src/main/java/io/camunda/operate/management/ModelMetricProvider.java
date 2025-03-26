/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.DecisionStore;
import io.camunda.operate.store.ProcessStore;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ModelMetricProvider {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired private ProcessStore processStore;

  @Autowired private DecisionStore decisionStore;

  @Autowired private Metrics metrics;

  @Autowired private OperateProperties operateProperties;

  private Long lastBPMNModelCount = 0L;
  private Long lastDMNModelCount = 0L;

  @PostConstruct
  private void registerMetrics() {
    logger.info("Register BPMN/DMN model metrics.");
    final String organizationId = operateProperties.getCloud().getOrganizationId();
    if (StringUtils.hasText(organizationId)) {
      metrics.registerGaugeSupplier(
          Metrics.GAUGE_BPMN_MODEL_COUNT,
          this::getBPMNModelCount,
          Metrics.TAG_KEY_ORGANIZATIONID,
          organizationId);
      metrics.registerGaugeSupplier(
          Metrics.GAUGE_DMN_MODEL_COUNT,
          this::getDMNModelCount,
          Metrics.TAG_KEY_ORGANIZATIONID,
          organizationId);
    } else {
      metrics.registerGaugeSupplier(Metrics.GAUGE_BPMN_MODEL_COUNT, this::getBPMNModelCount);
      metrics.registerGaugeSupplier(Metrics.GAUGE_DMN_MODEL_COUNT, this::getDMNModelCount);
    }
  }

  public Long getBPMNModelCount() {
    final Optional<Long> optionalCount =
        processStore.getDistinctCountFor(ProcessIndex.BPMN_PROCESS_ID);
    optionalCount.ifPresent(val -> lastBPMNModelCount = val);
    return lastBPMNModelCount;
  }

  public Long getDMNModelCount() {
    final Optional<Long> optionalCount =
        decisionStore.getDistinctCountFor(DecisionIndex.DECISION_ID);
    optionalCount.ifPresent(val -> lastDMNModelCount = val);
    return lastDMNModelCount;
  }
}
