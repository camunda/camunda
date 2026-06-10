/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {render, screen, within} from 'modules/testing-library';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockGetVariable} from 'modules/mocks/api/v2/variables/getVariable';
import {createVariable} from 'modules/testUtils';
import {ViewDocumentListButton} from './ViewDocumentListButton';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';

const VARIABLE_KEY = 'variable-key-123';

const preparsedDocuments: DocumentInfo[] = [
  {
    link: '/v2/documents/1',
    fileName: 'photo.png',
    type: 'image',
    contentType: 'image/png',
    size: 1024,
    isExpired: false,
  },
  {
    link: '/v2/documents/2',
    fileName: 'report.pdf',
    type: 'unknown',
    contentType: 'application/pdf',
    size: 2048,
    isExpired: false,
  },
  {
    link: '/v2/documents/3',
    fileName: 'notes.txt',
    type: 'unknown',
    contentType: 'text/plain',
    size: 256,
    isExpired: false,
  },
];

const buildDocumentReference = (
  documentId: string,
  fileName: string,
  contentType: string,
  size: number,
): DocumentReference => ({
  'camunda.document.type': 'camunda',
  storeId: 'in-memory',
  documentId,
  contentHash: `hash-${documentId}`,
  metadata: {
    fileName,
    contentType,
    size,
    expiresAt: null,
    processDefinitionId: null,
    processInstanceKey: null,
    customProperties: {},
  },
});

const fullVariableValue = JSON.stringify([
  buildDocumentReference('1', 'photo.png', 'image/png', 1024),
  buildDocumentReference('2', 'report.pdf', 'application/pdf', 2048),
  buildDocumentReference('3', 'notes.json', 'application/json', 256),
  buildDocumentReference('4', 'extra-1.png', 'image/png', 512),
  buildDocumentReference('5', 'extra-2.txt', 'text/plain', 4096),
]);

const createWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('<ViewDocumentListButton />', () => {
  it('should render a launcher button with the correct aria-label', () => {
    render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound={false}
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByLabelText('View documents for variable files'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should open the modal and not fetch the full variable when isLowerBound is false', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound={false}
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '3 documents in files'}),
    ).toBeInTheDocument();
    expect(
      dialog.queryByText(/Loading the full variable value.../),
    ).not.toBeInTheDocument();
    expect(
      dialog.queryByText(/Failed to load the full variable value/),
    ).not.toBeInTheDocument();
  });

  it('should show a list row for each document with filename and size', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound={false}
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    const photoItem = dialog.getByRole('listitem', {name: 'photo.png'});
    expect(photoItem).toBeInTheDocument();
    expect(photoItem).toHaveTextContent('1 KiB');
    const reportItem = dialog.getByRole('listitem', {name: 'report.pdf'});
    expect(reportItem).toBeInTheDocument();
    expect(reportItem).toHaveTextContent('2 KiB');
    const notesItem = dialog.getByRole('listitem', {name: 'notes.txt'});
    expect(notesItem).toBeInTheDocument();
  });

  it('should render preview and download buttons per document row', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound={false}
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getAllByLabelText('Preview document for variable files'),
    ).toHaveLength(3);
    expect(
      dialog.getAllByLabelText('Download document for variable files'),
    ).toHaveLength(3);
  });

  it('should show the truncated document list first, then replace it with the full list once loaded', async () => {
    mockGetVariable().withDelay(
      createVariable({variableKey: VARIABLE_KEY, value: fullVariableValue}),
    );

    const {user} = render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    // Full variable is still loading...
    expect(
      dialog.getByRole('heading', {name: '3+ documents in files'}),
    ).toBeInTheDocument();
    expect(dialog.getAllByRole('listitem')).toHaveLength(3);
    expect(
      dialog.getByText(
        'Loading the full variable value... More documents may exist for this variable.',
      ),
    ).toBeInTheDocument();
    expect(
      dialog.queryByText(/Failed to load the full variable value/),
    ).not.toBeInTheDocument();

    // ...full variable is loaded
    expect(
      await dialog.findByRole('heading', {name: '5 documents in files'}),
    ).toBeInTheDocument();
    expect(dialog.getAllByRole('listitem')).toHaveLength(5);
    expect(
      dialog.getByRole('listitem', {name: 'extra-1.png'}),
    ).toBeInTheDocument();
    expect(
      dialog.getByRole('listitem', {name: 'extra-2.txt'}),
    ).toBeInTheDocument();
    expect(
      dialog.queryByText(/Loading the full variable value.../),
    ).not.toBeInTheDocument();
    expect(
      dialog.queryByText(/Failed to load the full variable value/),
    ).not.toBeInTheDocument();
  });

  it('should keep the truncated document list and show an error notice when loading the full value fails', async () => {
    mockGetVariable().withServerError();

    const {user} = render(
      <ViewDocumentListButton
        documents={preparsedDocuments}
        isLowerBound
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      await dialog.findByText(
        'Failed to load the full variable value. More documents may exist for this variable.',
      ),
    ).toBeInTheDocument();
    expect(
      dialog.getByRole('heading', {name: '3+ documents in files'}),
    ).toBeInTheDocument();
    expect(dialog.getAllByRole('listitem')).toHaveLength(3);
    expect(
      dialog.queryByText(/Loading the full variable value.../),
    ).not.toBeInTheDocument();
  });

  it('should middle-truncate long file names inside the list', async () => {
    const longFileName = 'a'.repeat(40) + 'original-middle' + 'z'.repeat(40);
    const {user} = render(
      <ViewDocumentListButton
        documents={[
          {
            link: '/v2/documents/long',
            fileName: longFileName,
            type: 'unknown',
            contentType: 'application/octet-stream',
            size: 1024,
            isExpired: false,
          },
        ]}
        isLowerBound={false}
        variableKey={VARIABLE_KEY}
        variableName="files"
      />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    const fileNameEl = dialog.getByTitle(longFileName);
    expect(fileNameEl.textContent).toContain('\u2026');
    expect(fileNameEl.textContent).not.toContain('original-middle');
    expect(fileNameEl.textContent).not.toBe(longFileName);
  });
});
