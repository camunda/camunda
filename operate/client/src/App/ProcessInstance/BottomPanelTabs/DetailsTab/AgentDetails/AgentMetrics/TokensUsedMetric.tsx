/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LimitIndicator, MetricCard} from './MetricCard';
import {getTotalTokens, TOTAL_TOKENS_DESCRIPTION} from '../getTotalTokens';
import {
  TokenBreakdownContainer,
  TokenBreakdown,
  TokenBreakdownColumn,
} from './styled';

type TokensUsedMetricProps = {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokenCount: number;
  cacheCreationTokenCount: number;
  reasoningTokens: number;
  maxTokens: number;
};

const TokensUsedMetric: React.FC<TokensUsedMetricProps> = ({
  inputTokens,
  outputTokens,
  cacheReadTokenCount,
  cacheCreationTokenCount,
  reasoningTokens,
  maxTokens,
}) => {
  const totalTokens = getTotalTokens({
    inputTokens,
    outputTokens,
    cacheReadTokenCount,
    cacheCreationTokenCount,
  });

  return (
    <MetricCard
      title="Tokens Used"
      value={totalTokens}
      valueInfo={TOTAL_TOKENS_DESCRIPTION}
    >
      <LimitIndicator current={totalTokens} limit={maxTokens} />
      <TokenBreakdownContainer>
        <TokenBreakdownColumn>
          <TokenBreakdown $dotColor="var(--cds-interactive)">
            <span>Input</span>
            <span>{inputTokens.toLocaleString()}</span>
          </TokenBreakdown>
          <TokenBreakdown $dotColor="var(--cds-support-warning)">
            <span>Output</span>
            <span>{outputTokens.toLocaleString()}</span>
          </TokenBreakdown>
          {reasoningTokens > 0 && (
            <TokenBreakdown $dotColor="var(--cds-status-gray)">
              <span>Reasoning</span>
              <span>{reasoningTokens.toLocaleString()}</span>
            </TokenBreakdown>
          )}
        </TokenBreakdownColumn>
        {(cacheReadTokenCount > 0 || cacheCreationTokenCount > 0) && (
          <TokenBreakdownColumn>
            <TokenBreakdown $dotColor="var(--cds-status-gray)">
              <span>Cache read</span>
              <span>{cacheReadTokenCount.toLocaleString()}</span>
            </TokenBreakdown>
            <TokenBreakdown $dotColor="var(--cds-status-gray)">
              <span>Cache write</span>
              <span>{cacheCreationTokenCount.toLocaleString()}</span>
            </TokenBreakdown>
          </TokenBreakdownColumn>
        )}
      </TokenBreakdownContainer>
    </MetricCard>
  );
};

export {TokensUsedMetric};
