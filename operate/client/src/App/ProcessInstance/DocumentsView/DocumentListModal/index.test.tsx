/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import type {DocumentInfo} from '../documentInfo';
import {DocumentListModal} from './index';

const documents: DocumentInfo[] = [
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

describe('<DocumentListModal />', () => {
  it('should show a list row for each document with filename and size', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded
        isLoading={false}
        isError={false}
        variableName="files"
      />,
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

  it('should render preview and download buttons per document row', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded
        isLoading={false}
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getAllByLabelText('Preview document for variable files'),
    ).toHaveLength(3);
    expect(
      dialog.getAllByLabelText('Download document for variable files'),
    ).toHaveLength(3);
  });

  it('should show the exact document count when fully loaded', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded
        isLoading={false}
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '3 documents in files'}),
    ).toBeInTheDocument();
  });

  it('should show a lower-bound count with a plus sign when not fully loaded', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded={false}
        isLoading={false}
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '3+ documents in files'}),
    ).toBeInTheDocument();
  });

  it('should show a loading notice when isLoading is true', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded={false}
        isLoading
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByText(
        'Loading the full variable value... More documents may exist for this variable.',
      ),
    ).toBeInTheDocument();
  });

  it('should show an error notice when isError is true', () => {
    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documents}
        isFullyLoaded={false}
        isLoading={false}
        isError
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByText(
        'Failed to load the full variable value. More documents may exist for this variable.',
      ),
    ).toBeInTheDocument();
  });

  it('should middle-truncate long file names inside the list', () => {
    const longFileName = 'a'.repeat(40) + 'original-middle' + 'z'.repeat(40);

    render(
      <DocumentListModal
        open
        setOpen={() => {}}
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
        isFullyLoaded
        isLoading={false}
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));
    const fileNameEl = dialog.getByTitle(longFileName);
    expect(fileNameEl.textContent).toContain('…');
    expect(fileNameEl.textContent).not.toContain('original-middle');
    expect(fileNameEl.textContent).not.toBe(longFileName);
  });

  it('should mark an expired document row and disable its preview and download buttons', async () => {
    const documentsWithExpired: DocumentInfo[] = [
      {
        link: '/v2/documents/active',
        fileName: 'active.png',
        type: 'image',
        contentType: 'image/png',
        size: 1024,
        isExpired: false,
      },
      {
        link: '/v2/documents/expired',
        fileName: 'expired.png',
        type: 'image',
        contentType: 'image/png',
        size: 2048,
        isExpired: true,
      },
    ];

    render(
      <DocumentListModal
        open
        setOpen={() => {}}
        documents={documentsWithExpired}
        isFullyLoaded
        isLoading={false}
        isError={false}
        variableName="files"
      />,
    );

    const dialog = within(screen.getByRole('dialog'));

    const expiredItem = within(
      dialog.getByRole('listitem', {name: 'expired.png'}),
    );
    expect(expiredItem.getByText('Expired')).toBeInTheDocument();
    expect(
      expiredItem.getByLabelText('Preview document for variable files'),
    ).toBeDisabled();
    expect(
      expiredItem.getByLabelText('Download document for variable files'),
    ).toBeDisabled();

    const activeItem = within(
      dialog.getByRole('listitem', {name: 'active.png'}),
    );
    expect(activeItem.queryByText('Expired')).not.toBeInTheDocument();
    expect(
      activeItem.getByLabelText('Preview document for variable files'),
    ).toBeEnabled();
    const activeDownloadLink = activeItem.getByLabelText(
      'Download document for variable files',
    );
    expect(activeDownloadLink).toBeEnabled();
    expect(activeDownloadLink).toHaveAttribute('href', '/v2/documents/active');
    expect(activeDownloadLink).toHaveAttribute('download', 'active.png');
  });
});
