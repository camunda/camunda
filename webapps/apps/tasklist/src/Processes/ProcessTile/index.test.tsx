/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessTile} from '.';
import {render, screen, within} from 'modules/testing-library';
import {createMockProcess} from 'modules/queries/useProcesses';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {MemoryRouter} from 'react-router-dom';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={['/processes']}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('ProcessTile', () => {
  it('should show the title of the process', () => {
    render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('heading', {name: 'Process processId'}),
    ).toBeInTheDocument();
  });

  it('should show the Start Process button', () => {
    render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
      />,
      {wrapper: getWrapper()},
    );

    const tile = screen.getByTestId('process-tile-content', {exact: true});
    expect(tile).toBeInTheDocument();
    expect(
      within(tile).getByRole('button', {name: 'Start process'}),
    ).toBeVisible();
  });

  it('should show the Requires Form Input tag if the process has a start form', () => {
    render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
      />,
      {wrapper: getWrapper()},
    );

    const tagList = screen.getByRole('list', {name: 'Process Attributes'});
    expect(tagList).toBeInTheDocument();
    expect(
      within(tagList).getByText('Requires form input'),
    ).toBeInTheDocument();
  });
});
