/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DrainingTag} from './index';

describe('<DrainingTag />', () => {
  it('should render the draining tag', () => {
    render(<DrainingTag description="Scheduled for deletion" />);

    expect(screen.getByTestId('draining-tag')).toBeInTheDocument();
    expect(screen.getByText('Draining')).toBeInTheDocument();
  });

  it('should show the description as tooltip on hover', async () => {
    const {user} = render(<DrainingTag description="Scheduled for deletion" />);

    await user.hover(screen.getByTestId('draining-tag'));

    expect(
      await screen.findByText('Scheduled for deletion'),
    ).toBeInTheDocument();
  });
});
