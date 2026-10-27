/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LimitIndicator, MetricCard} from './MetricCard';

type ModelCallsMetricProps = {
  modelCalls: number;
  maxModelCalls: number;
};

const ModelCallsMetric: React.FC<ModelCallsMetricProps> = ({
  modelCalls,
  maxModelCalls,
}) => {
  return (
    <MetricCard title="Model Calls" value={modelCalls}>
      <LimitIndicator current={modelCalls} limit={maxModelCalls} />
    </MetricCard>
  );
};

export {ModelCallsMetric};
