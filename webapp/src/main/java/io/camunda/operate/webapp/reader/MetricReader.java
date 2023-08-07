/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.es.contract.MetricContract;

import java.time.OffsetDateTime;

public interface MetricReader extends MetricContract.Reader {
    String PROCESS_INSTANCES_AGG_NAME = "process_instances";
    String DECISION_INSTANCES_AGG_NAME = "decision_instances";

    @Override
    Long retrieveProcessInstanceCount(OffsetDateTime startTime, OffsetDateTime endTime);

    @Override
    Long retrieveDecisionInstanceCount(OffsetDateTime startTime,
                                       OffsetDateTime endTime);
}
