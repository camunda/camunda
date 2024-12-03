/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {Component as AttachmentsView} from './index';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as attachmentsMocks from 'modules/mock-schema/mocks/attachments';

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter initialEntries={[`/${id}`]}>
        <Routes>
          <Route path="/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<AttachmentsView />', () => {
  it('should render the attachments list', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/tasks/:taskId/attachments',
        () => HttpResponse.json(attachmentsMocks.attachments),
        {once: true},
      ),
    );

    render(<AttachmentsView />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('attachment-list-skeleton'),
    );

    expect(screen.getByRole('list', {name: '2 files'})).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(
      screen.getByRole('listitem', {name: 'file-1.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'file-2.txt'}),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', {name: 'Download'})).toHaveLength(2);
    expect(screen.getAllByRole('button', {name: 'Preview'})).toHaveLength(2);
  });

  it('should show an error message', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/tasks/:taskId/attachments',
        () => new HttpResponse(null, {status: 404}),
        {once: true},
      ),
    );

    render(<AttachmentsView />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('attachment-list-skeleton'),
    );

    expect(
      screen.getByRole('heading', {
        name: 'Error retrieving attachments',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Something went wrong and the attachments could not be retrieved. Please contact your provider',
      ),
    ).toBeInTheDocument();
  });
});
