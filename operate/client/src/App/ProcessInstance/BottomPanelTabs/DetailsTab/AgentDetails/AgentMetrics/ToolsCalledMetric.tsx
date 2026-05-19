/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MetricCard} from './MetricCard';
import {MetricHelperText} from './styled';

type ToolsCalledMetricProps = {
  toolCalls: number;
};

const ToolsCalledMetric: React.FC<ToolsCalledMetricProps> = ({toolCalls}) => {
  return (
    <MetricCard title="Tools Called" value={toolCalls}>
      <MetricHelperText>
        Across all model calls in this instance.
      </MetricHelperText>
    </MetricCard>
  );
};

export {ToolsCalledMetric};
