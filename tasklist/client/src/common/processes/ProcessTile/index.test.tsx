/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessTile} from '.';
import {render, screen, waitFor, within} from 'common/testing/testing-library';
import {createMockProcess} from 'v1/api/useProcesses.query';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
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
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
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
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
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
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
      />,
      {wrapper: getWrapper()},
    );

    const tagList = screen.getByRole('list', {name: 'Process Attributes'});
    expect(tagList).toBeInTheDocument();
    expect(
      within(tagList).getByText('Requires form input'),
    ).toBeInTheDocument();
  });

  it('should disable start button', () => {
    render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
      />,
      {wrapper: getWrapper()},
    );

    const tile = screen.getByTestId('process-tile-content', {exact: true});
    expect(tile).toBeInTheDocument();
    expect(
      within(tile).getByRole('button', {name: 'Start process'}),
    ).toBeDisabled();
  });

  it('should call onStartProcess', () => {
    const onStartProcess = vi.fn();

    render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
        onStartProcess={onStartProcess}
        onStartProcessError={() => {}}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
      />,
      {wrapper: getWrapper()},
    );

    const tile = screen.getByTestId('process-tile-content', {exact: true});
    expect(tile).toBeInTheDocument();
    expect(onStartProcess).not.toHaveBeenCalled();

    within(tile).getByRole('button', {name: 'Start process'}).click();
    expect(onStartProcess).toHaveBeenCalled();
  });

  it('should call onStartProcessError', async () => {
    const onStartProcessError = vi.fn();

    const {rerender} = render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
        onStartProcess={() => {}}
        onStartProcessError={onStartProcessError}
        onStartProcessSuccess={() => {}}
        status="inactive"
        displayName="Process processId"
      />,
      {wrapper: getWrapper()},
    );

    expect(onStartProcessError).not.toHaveBeenCalled();

    rerender(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
        onStartProcess={() => {}}
        onStartProcessError={onStartProcessError}
        onStartProcessSuccess={() => {}}
        status="error"
        displayName="Process processId"
      />,
    );

    await waitFor(() => {
      expect(onStartProcessError).toHaveBeenCalled();
    });
  });

  it('should call onStartProcessSuccess', async () => {
    const onStartProcessSuccess = vi.fn();

    const {rerender} = render(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={onStartProcessSuccess}
        status="inactive"
        displayName="Process processId"
      />,
      {wrapper: getWrapper()},
    );

    expect(onStartProcessSuccess).not.toHaveBeenCalled();

    rerender(
      <ProcessTile
        process={createMockProcess('processId')}
        isFirst={false}
        isStartButtonDisabled={false}
        onStartProcess={() => {}}
        onStartProcessError={() => {}}
        onStartProcessSuccess={onStartProcessSuccess}
        status="finished"
        displayName="Process processId"
      />,
    );

    await waitFor(() => {
      expect(onStartProcessSuccess).toHaveBeenCalled();
    });
  });
});
