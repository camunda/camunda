/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItemMetrics} from '@camunda/camunda-api-zod-schemas/8.10';
import {Tag, Tooltip} from '@carbon/react';
import {memo} from 'react';
import styled from 'styled-components';
import {getTotalTokens} from '../getTotalTokens';

const MetricsContainer = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  --cds-tooltip-padding-block: var(--cds-spacing-02);
  --cds-tooltip-padding-inline: var(--cds-spacing-03);
`;

function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`;
}

function formatToken(tokens: number | null): string {
  return tokens?.toLocaleString() ?? '---';
}

type Props = {
  metrics: AgentInstanceHistoryItemMetrics | null;
};

const MessageMetrics: React.FC<Props> = memo(function MessageMetrics({
  metrics,
}) {
  if (metrics === null) {
    return null;
  }

  const hasTokenMetrics =
    metrics.inputTokens !== null ||
    metrics.outputTokens !== null ||
    metrics.cacheReadTokenCount !== null ||
    metrics.cacheCreationTokenCount !== null;

  if (!hasTokenMetrics && metrics.durationMs === null) {
    return null;
  }

  const totalTokensMetric = getTotalTokens(metrics);
  const inputTooltip = `Input: ${formatToken(metrics.inputTokens)}`;
  const cacheReadTooltip =
    metrics.cacheReadTokenCount !== null
      ? `Cache read: ${formatToken(metrics.cacheReadTokenCount)}`
      : '';
  const outputTooltip = `Output: ${formatToken(metrics.outputTokens)} ${metrics.reasoningTokenCount !== null ? `(${formatToken(metrics.reasoningTokenCount)} reasoning)` : ''}`;
  const cacheWriteTooltip =
    metrics.cacheCreationTokenCount !== null
      ? `Cache write: ${formatToken(metrics.cacheCreationTokenCount)}`
      : '';

  return (
    <MetricsContainer>
      {hasTokenMetrics && (
        <Tooltip
          description={
            <>
              {inputTooltip}
              <br />
              {cacheReadTooltip}
              <br />
              {cacheWriteTooltip}
              <br />
              {outputTooltip}
            </>
          }
          align="bottom"
        >
          <Tag data-testid="message-token-metric" type="gray" size="sm">
            {totalTokensMetric.toLocaleString()}
            &nbsp;tokens
          </Tag>
        </Tooltip>
      )}
      {metrics.durationMs !== null && (
        <Tag data-testid="message-duration-metric" type="gray" size="sm">
          {formatDuration(metrics.durationMs)}
        </Tag>
      )}
    </MetricsContainer>
  );
});

export {MessageMetrics};
