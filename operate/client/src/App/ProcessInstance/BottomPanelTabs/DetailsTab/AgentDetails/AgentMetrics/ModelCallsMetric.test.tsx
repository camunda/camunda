/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ModelCallsMetric} from './ModelCallsMetric';

describe('<ModelCallsMetric />', () => {
  it('should render model calls and limit indicator', () => {
    render(<ModelCallsMetric modelCalls={5} maxModelCalls={10} />);

    const container = screen.getByRole('article', {name: 'Model Calls'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('5')).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '5 of 10 limit (50%)');
  });

  it('should hide the limit indicator when no limit is set', () => {
    render(<ModelCallsMetric modelCalls={5} maxModelCalls={-1} />);

    const container = screen.getByRole('article', {name: 'Model Calls'});

    expect(container).toBeInTheDocument();
    expect(within(container).getByText('5')).toBeInTheDocument();
    expect(
      within(container).queryByRole('meter', {name: 'Usage limit'}),
    ).toBeNull();
  });

  it('should show 100% usage when limit is set to 0', () => {
    render(<ModelCallsMetric modelCalls={3} maxModelCalls={0} />);

    const container = screen.getByRole('article', {name: 'Model Calls'});
    const limit = within(container).getByRole('meter', {name: 'Usage limit'});

    expect(container).toBeInTheDocument();
    expect(limit).toHaveAttribute('aria-valuetext', '3 of 0 limit (100%)');
  });
});
