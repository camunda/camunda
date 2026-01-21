/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('NoInstancesEmptyState', () => {
  it('should display empty state message', () => {
    mockMe().withSuccess(createUser());

    render(<NoInstancesEmptyState />, {wrapper: Wrapper});

    expect(
      screen.getByText('Start by deploying a process'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress.',
      ),
    ).toBeInTheDocument();
  });

  it('should render modeler button when link is available', async () => {
    mockMe().withSuccess(
      createUser({
        c8Links: [{name: 'modeler', link: 'https://link-to-modeler'}],
      }),
    );

    render(<NoInstancesEmptyState />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('button', {name: 'Go to Modeler'}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: 'Go to Modeler'}).closest('a'),
    ).toHaveAttribute('href', 'https://link-to-modeler');
  });

  it('should not render modeler button when link is unavailable', async () => {
    mockMe().withSuccess(createUser());

    render(<NoInstancesEmptyState />, {wrapper: Wrapper});

    await screen.findByText('Start by deploying a process');

    expect(
      screen.queryByRole('button', {name: 'Go to Modeler'}),
    ).not.toBeInTheDocument();
  });

  it('should render docs link', () => {
    mockMe().withSuccess(createUser());

    render(<NoInstancesEmptyState />, {wrapper: Wrapper});

    const docsLink = screen.getByText('Learn more about Operate').closest('a');
    expect(docsLink).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/components/operate/operate-introduction/',
    );
  });
});
