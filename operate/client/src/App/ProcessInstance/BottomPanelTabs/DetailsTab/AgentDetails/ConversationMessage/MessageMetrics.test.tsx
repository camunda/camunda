/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItemMetrics} from '@camunda/camunda-api-zod-schemas/8.10';
import {render, screen} from 'modules/testing-library';
import {MessageMetrics} from './MessageMetrics';

const FULL_METRICS: AgentInstanceHistoryItemMetrics = {
  inputTokens: 12345,
  outputTokens: 456,
  cacheCreationTokenCount: 10000,
  cacheReadTokenCount: 7000,
  reasoningTokenCount: 200,
  durationMs: 2500,
};

describe('<MessageMetrics />', () => {
  it('should render nothing when metrics is null', () => {
    const {container} = render(<MessageMetrics metrics={null} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('should render nothing when every metric field is null', () => {
    const {container} = render(
      <MessageMetrics
        metrics={{
          inputTokens: null,
          outputTokens: null,
          cacheCreationTokenCount: null,
          cacheReadTokenCount: null,
          reasoningTokenCount: null,
          durationMs: null,
        }}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('should render token and duration tags when all metrics are present', async () => {
    const {user} = render(<MessageMetrics metrics={FULL_METRICS} />);

    expect(screen.getByTestId('message-token-metric')).toHaveTextContent(
      '12,801 tokens',
    );
    expect(screen.getByTestId('message-duration-metric')).toHaveTextContent(
      '2.50s',
    );

    await user.hover(screen.getByTestId('message-token-metric'));
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Input: 12,345 (7,000 cached)');
    expect(tooltip).toHaveTextContent('Output: 456 (200 reasoning)');
    expect(tooltip).toHaveTextContent('Cache write: 10,000');
  });

  it('should render token and duration tags when all metrics are 0', async () => {
    const {user} = render(
      <MessageMetrics
        metrics={{
          inputTokens: 0,
          outputTokens: 0,
          cacheCreationTokenCount: 0,
          cacheReadTokenCount: 0,
          reasoningTokenCount: 0,
          durationMs: 0,
        }}
      />,
    );

    expect(screen.getByTestId('message-token-metric')).toHaveTextContent(
      '0 tokens',
    );
    expect(screen.getByTestId('message-duration-metric')).toHaveTextContent(
      '0ms',
    );

    await user.hover(screen.getByTestId('message-token-metric'));
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Input: 0 (0 cached)');
    expect(tooltip).toHaveTextContent('Output: 0 (0 reasoning)');
    expect(tooltip).toHaveTextContent('Cache write: 0');
  });

  it('should treat a null inputTokens as 0 but show --- in the tooltip', async () => {
    const {user} = render(
      <MessageMetrics
        metrics={{
          ...FULL_METRICS,
          inputTokens: null,
          cacheReadTokenCount: null,
        }}
      />,
    );

    expect(screen.getByTestId('message-token-metric')).toHaveTextContent(
      '456 tokens',
    );

    await user.hover(screen.getByTestId('message-token-metric'));
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Input: ---');
  });

  it('should treat a null outputTokens as 0 but show --- in the tooltip', async () => {
    const {user} = render(
      <MessageMetrics
        metrics={{
          ...FULL_METRICS,
          outputTokens: null,
          reasoningTokenCount: null,
        }}
      />,
    );

    expect(screen.getByTestId('message-token-metric')).toHaveTextContent(
      '12,345 tokens',
    );

    await user.hover(screen.getByTestId('message-token-metric'));
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Output: ---');
  });

  it('should omit the cached/reasoning/cache write hints when their metrics are null', async () => {
    const {user} = render(
      <MessageMetrics
        metrics={{
          ...FULL_METRICS,
          cacheReadTokenCount: null,
          reasoningTokenCount: null,
          cacheCreationTokenCount: null,
        }}
      />,
    );

    await user.hover(screen.getByTestId('message-token-metric'));
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Input: 12,345');
    expect(tooltip).not.toHaveTextContent('cached');
    expect(tooltip).toHaveTextContent('Output: 456');
    expect(tooltip).not.toHaveTextContent('reasoning');
    expect(tooltip).not.toHaveTextContent('Cache write');
  });

  it('should not render the token tag when both inputTokens and outputTokens are null', () => {
    render(
      <MessageMetrics
        metrics={{...FULL_METRICS, inputTokens: null, outputTokens: null}}
      />,
    );

    expect(
      screen.queryByTestId('message-token-metric'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('message-duration-metric')).toBeInTheDocument();
  });

  it('should not render the duration tag when durationMs is null', () => {
    render(<MessageMetrics metrics={{...FULL_METRICS, durationMs: null}} />);

    expect(screen.getByTestId('message-token-metric')).toBeInTheDocument();
    expect(
      screen.queryByTestId('message-duration-metric'),
    ).not.toBeInTheDocument();
  });

  it('should format durations under 1 second in milliseconds', () => {
    render(<MessageMetrics metrics={{...FULL_METRICS, durationMs: 999}} />);
    expect(screen.getByTestId('message-duration-metric')).toHaveTextContent(
      '999ms',
    );
  });

  it('should format durations of 1 second or more in seconds with two decimals', () => {
    render(<MessageMetrics metrics={{...FULL_METRICS, durationMs: 1000}} />);
    expect(screen.getByTestId('message-duration-metric')).toHaveTextContent(
      '1.00s',
    );
  });
});
