/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ViewDocumentListButton} from './ViewDocumentListButton';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';

const documents: DocumentInfo[] = [
  {
    link: '/v2/documents/1',
    fileName: 'photo.png',
    type: 'image',
    size: 1024,
  },
  {
    link: '/v2/documents/2',
    fileName: 'report.pdf',
    type: 'unknown',
    size: 2048,
  },
  {
    link: '/v2/documents/3',
    fileName: 'notes.txt',
    type: 'unknown',
    size: undefined,
  },
];

describe('<ViewDocumentListButton />', () => {
  it('should render a launcher button with the correct aria-label', () => {
    render(
      <ViewDocumentListButton
        documents={documents}
        isLowerBound={false}
        variableName="files"
      />,
    );

    expect(
      screen.getByLabelText('View documents for variable files'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should open the modal with the count heading when clicked', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={documents}
        isLowerBound={false}
        variableName="files"
      />,
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '3 documents in files'}),
    ).toBeInTheDocument();
  });

  it('should show a list row for each document with filename and size', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={documents}
        isLowerBound={false}
        variableName="files"
      />,
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
        documents={documents}
        isLowerBound={false}
        variableName="files"
      />,
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

  it('should show truncation notices when isLowerBound is true', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={documents}
        isLowerBound
        variableName="files"
      />,
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '3+ documents in files'}),
    ).toBeInTheDocument();
    expect(
      dialog.getByText('More documents may exist for this variable.'),
    ).toBeInTheDocument();
  });

  it('should not show a truncation notice when isLowerBound is false', async () => {
    const {user} = render(
      <ViewDocumentListButton
        documents={documents}
        isLowerBound={false}
        variableName="files"
      />,
    );

    await user.click(
      screen.getByLabelText('View documents for variable files'),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.queryByText('More documents may exist for this variable.'),
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
            size: 1024,
          },
        ]}
        isLowerBound={false}
        variableName="files"
      />,
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
