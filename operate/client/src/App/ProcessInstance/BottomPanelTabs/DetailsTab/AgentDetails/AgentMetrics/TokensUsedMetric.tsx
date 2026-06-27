/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LimitIndicator, MetricCard} from './MetricCard';
import {TokenBreakdownContainer, TokenBreakdown} from './styled';

type TokensUsedMetricProps = {
  inputTokens: number;
  outputTokens: number;
  maxTokens: number;
};

const TokensUsedMetric: React.FC<TokensUsedMetricProps> = ({
  inputTokens,
  outputTokens,
  maxTokens,
}) => {
  const totalTokens = inputTokens + outputTokens;

  return (
    <MetricCard title="Tokens Used" value={totalTokens}>
      <LimitIndicator current={totalTokens} limit={maxTokens} />
      <TokenBreakdownContainer>
        <TokenBreakdown $dotColor="var(--cds-interactive)">
          <span>Input</span>
          <span>{inputTokens.toLocaleString()}</span>
        </TokenBreakdown>
        <TokenBreakdown $dotColor="var(--cds-support-warning)">
          <span>Output</span>
          <span>{outputTokens.toLocaleString()}</span>
        </TokenBreakdown>
      </TokenBreakdownContainer>
    </MetricCard>
  );
};

export {TokensUsedMetric};
