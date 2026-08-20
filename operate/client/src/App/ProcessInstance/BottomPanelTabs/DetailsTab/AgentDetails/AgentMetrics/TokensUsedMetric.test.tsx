/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {TokensUsedMetric} from './TokensUsedMetric';

describe('<TokensUsedMetric />', () => {
  it('should render total tokens calls and limit indicator', () => {
    render(
      <TokensUsedMetric inputTokens={4} outputTokens={2} maxTokens={10} />,
    );

    const container = screen.getByRole('article', {name: 'Tokens Used'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('6')).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '6 of 10 limit (60%)');
  });

  it('should render separated input and output tokens information', () => {
    render(
      <TokensUsedMetric inputTokens={4} outputTokens={2} maxTokens={10} />,
    );

    const container = screen.getByRole('article', {name: 'Tokens Used'});
    expect(container).toBeInTheDocument();
    expect(within(container).getByText('Input')).toBeInTheDocument();
    expect(within(container).getByText('4')).toBeInTheDocument();
    expect(within(container).getByText('Output')).toBeInTheDocument();
    expect(within(container).getByText('2')).toBeInTheDocument();
  });

  it('should hide the limit indicator when no limit is set', () => {
    render(
      <TokensUsedMetric inputTokens={4} outputTokens={2} maxTokens={-1} />,
    );

    const container = screen.getByRole('article', {name: 'Tokens Used'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('6')).toBeInTheDocument();
    expect(
      within(container).queryByRole('meter', {name: 'Usage limit'}),
    ).toBeNull();
  });

  it('should show 100% usage when limit is set to 0', () => {
    render(<TokensUsedMetric inputTokens={3} outputTokens={0} maxTokens={0} />);

    const container = screen.getByRole('article', {name: 'Tokens Used'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '3 of 0 limit (100%)');
  });
});
