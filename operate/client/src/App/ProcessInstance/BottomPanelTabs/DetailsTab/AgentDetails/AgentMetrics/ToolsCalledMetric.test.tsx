/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ToolsCalledMetric} from './ToolsCalledMetric';

describe('<ToolsCalledMetric />', () => {
  it('should render tools called and limit indicator', () => {
    render(<ToolsCalledMetric toolCalls={6} maxToolCalls={10} />);

    const container = screen.getByRole('article', {name: 'Tools Called'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('6')).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '6 of 10 limit (60%)');
    expect(
      within(container).getByText('Across all model calls in this instance.'),
    ).toBeInTheDocument();
  });

  it('should hide the limit indicator when no limit is set', () => {
    render(<ToolsCalledMetric toolCalls={5} maxToolCalls={-1} />);

    const container = screen.getByRole('article', {name: 'Tools Called'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('5')).toBeInTheDocument();
    expect(
      within(container).queryByRole('meter', {name: 'Usage limit'}),
    ).toBeNull();
  });

  it('should show 100% usage when limit is set to 0', () => {
    render(<ToolsCalledMetric toolCalls={3} maxToolCalls={0} />);

    const container = screen.getByRole('article', {name: 'Tools Called'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '3 of 0 limit (100%)');
  });
});
