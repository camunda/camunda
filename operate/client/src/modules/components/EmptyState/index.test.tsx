/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ReactComponent as EmptyStateProcessIncidents} from 'modules/components/Icon/empty-state-process-incidents.svg';
import {EmptyState} from '.';

describe('EmptyState', () => {
  it('should render EmptyState with button and link', async () => {
    const buttonSpy = jest.fn();
    const linkSpy = jest.fn();

    const {user} = render(
      <EmptyState
        heading="Nothing to see"
        description="Please move on"
        icon={<EmptyStateProcessIncidents title="Alt Text" />}
        link={{href: '/link-to-home', label: 'Go Home', onClick: linkSpy}}
      />,
    );

    expect(screen.getByText('Nothing to see')).toBeInTheDocument();
    expect(screen.getByText('Please move on')).toBeInTheDocument();
    expect(
      screen.getByText('empty-state-process-incidents.svg'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('link', {name: 'Go Home'}));
    expect(linkSpy).toHaveBeenCalledTimes(1);

    buttonSpy.mockClear();
    linkSpy.mockClear();
  });

  it('should render EmptyState without button and link', () => {
    render(
      <EmptyState
        heading="Nothing to see"
        description="Please move on"
        icon={<EmptyStateProcessIncidents title="Alt Text" />}
      />,
    );

    expect(screen.getByText('Nothing to see')).toBeInTheDocument();
    expect(screen.getByText('Please move on')).toBeInTheDocument();
    expect(
      screen.getByText('empty-state-process-incidents.svg'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
