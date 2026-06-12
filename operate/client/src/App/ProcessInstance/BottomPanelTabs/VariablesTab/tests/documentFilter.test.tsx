/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {createVariable} from 'modules/testUtils';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {getWrapper, mockProcessInstance} from './mocks';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';

const DOCUMENT_VALUE = JSON.stringify({
  'camunda.document.type': 'camunda',
  storeId: 'in-memory',
  documentId: 'doc-123',
  contentHash: 'sha256:abc',
  metadata: {
    contentType: 'image/png',
    fileName: 'photo.png',
    expiresAt: null,
    size: 109748,
    processDefinitionId: null,
    processInstanceKey: null,
    customProperties: {},
  },
});

const allVariables = {
  items: [
    createVariable({name: 'regularVar', value: '"hello"'}),
    createVariable({name: 'documentVar', value: DOCUMENT_VALUE}),
  ],
  page: {
    totalItems: 2,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

// The server-side $like also matches values that merely contain the document
// marker text (e.g. plain strings or nested objects). These must be filtered
// out client-side so only actual document variables are shown.
const NESTED_DECOY_VALUE = JSON.stringify({
  wrapper: {'camunda.document.type': 'camunda', storeId: 'x'},
});
const STRING_DECOY_VALUE = JSON.stringify(
  'please attach your camunda.document.type here',
);

const documentOnlyVariables = {
  items: [
    createVariable({name: 'documentVar', value: DOCUMENT_VALUE}),
    createVariable({name: 'nestedDecoy', value: NESTED_DECOY_VALUE}),
    createVariable({name: 'stringDecoy', value: STRING_DECOY_VALUE}),
  ],
  page: {
    totalItems: 3,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

describe('VariablesTab - document filter', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    mockFetchElementInstancesStatistics().withSuccess({items: []});
    mockFetchElementInstancesStatistics().withSuccess({items: []});
    mockFetchElementInstancesStatistics().withSuccess({items: []});

    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    // Persistent handler: returns filtered or all variables based on request body
    mockServer.use(
      http.post('/v2/variables/search', async ({request}) => {
        const body = (await request.json()) as {
          filter?: {value?: {$like?: string}};
        };
        const isDocumentFilter =
          body?.filter?.value?.$like === '*camunda.document.type*';
        return HttpResponse.json(
          isDocumentFilter ? documentOnlyVariables : allVariables,
        );
      }),
    );
  });

  it('should request only document variables when filter is active', async () => {
    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('regularVar')).toBeInTheDocument();
    expect(screen.getByText('documentVar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Documents'}));

    await waitFor(() => {
      expect(screen.queryByText('regularVar')).not.toBeInTheDocument();
    });
    expect(screen.getByText('documentVar')).toBeInTheDocument();
  });

  it('should show all variables when filter is toggled off', async () => {
    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('regularVar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Documents'}));
    await waitFor(() => {
      expect(screen.queryByText('regularVar')).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', {name: 'All'}));
    await waitFor(() => {
      expect(screen.getByText('regularVar')).toBeInTheDocument();
    });
    expect(screen.getByText('documentVar')).toBeInTheDocument();
  });

  it('should exclude non-document values that only contain the document marker', async () => {
    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('regularVar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Documents'}));

    await waitFor(() => {
      expect(screen.queryByText('regularVar')).not.toBeInTheDocument();
    });
    expect(screen.getByText('documentVar')).toBeInTheDocument();
    expect(screen.queryByText('nestedDecoy')).not.toBeInTheDocument();
    expect(screen.queryByText('stringDecoy')).not.toBeInTheDocument();
  });

  it('should show an empty state when there are no document variables', async () => {
    // Override: the document-filtered response contains only false positives
    mockServer.use(
      http.post('/v2/variables/search', async ({request}) => {
        const body = (await request.json()) as {
          filter?: {value?: {$like?: string}};
        };
        const isDocumentFilter =
          body?.filter?.value?.$like === '*camunda.document.type*';
        return HttpResponse.json(
          isDocumentFilter
            ? {
                items: [
                  createVariable({
                    name: 'nestedDecoy',
                    value: NESTED_DECOY_VALUE,
                  }),
                  createVariable({
                    name: 'stringDecoy',
                    value: STRING_DECOY_VALUE,
                  }),
                ],
                page: {
                  totalItems: 2,
                  startCursor: null,
                  endCursor: null,
                  hasMoreTotalItems: false,
                },
              }
            : allVariables,
        );
      }),
    );

    const {user} = render(<VariablesTab />, {wrapper: getWrapper()});

    expect(await screen.findByText('regularVar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Documents'}));

    expect(
      await screen.findByText('There are no document variables'),
    ).toBeInTheDocument();
    expect(screen.queryByText('nestedDecoy')).not.toBeInTheDocument();
    expect(screen.queryByText('stringDecoy')).not.toBeInTheDocument();
  });
});
