/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {DocumentContent} from './DocumentContent';
import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';

type ContentItem = AgentInstanceHistoryItem['content'][number];

function createDocumentContent(
  id: string,
  fileName: string,
  contentType = 'application/pdf',
): Extract<ContentItem, {contentType: 'DOCUMENT'}> {
  return {
    contentType: 'DOCUMENT',
    documentReference: {
      'camunda.document.type': 'camunda',
      contentHash: id,
      documentId: id,
      storeId: 'default',
      metadata: {
        contentType,
        fileName,
        size: 100,
        expiresAt: null,
        processDefinitionId: null,
        processInstanceKey: null,
        customProperties: {},
      },
    },
  };
}

describe('<DocumentContent />', () => {
  it('should render nothing when there are no document entries', () => {
    const {container} = render(
      <DocumentContent
        content={[{contentType: 'TEXT', text: 'hello'}]}
        modalTitleSuffix="conversation message"
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('should render a card with a filename, size, preview and download for each document', () => {
    const doc1 = createDocumentContent('doc-1', 'report.txt');
    const doc2 = createDocumentContent('doc-2', 'screenshot.png');
    render(
      <DocumentContent
        content={[doc1, doc2]}
        modalTitleSuffix="conversation message"
      />,
    );

    expect(
      screen.getByRole('listitem', {name: 'report.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'screenshot.png'}),
    ).toBeInTheDocument();
    const card = within(screen.getByRole('listitem', {name: 'report.txt'}));
    expect(card.getByText('100 B')).toBeInTheDocument();
    expect(
      card.getByRole('button', {name: 'Preview document for report.txt'}),
    ).toBeEnabled();
    expect(card.getByRole('link', {name: 'Download'})).toHaveAttribute(
      'download',
      'report.txt',
    );
    expect(screen.queryByLabelText('View documents')).not.toBeInTheDocument();
  });

  it('should retain the full filename for accessible preview and hover text', () => {
    const doc1 = createDocumentContent(
      'doc-1',
      'a-very-long-document-name.txt',
    );
    render(
      <DocumentContent
        content={[doc1]}
        modalTitleSuffix="conversation message"
      />,
    );

    expect(
      screen.getByRole('button', {
        name: 'Preview document for a-very-long-document-name.txt',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle('a-very-long-document-name.txt'),
    ).toHaveTextContent('a-very-long-document-name.txt');
  });

  it('should limit inline cards and show all documents when expanded', () => {
    const content = [
      createDocumentContent('doc-1', 'first.txt'),
      createDocumentContent('doc-2', 'second.txt'),
      createDocumentContent('doc-3', 'third.txt'),
      createDocumentContent('doc-4', 'fourth.txt'),
      createDocumentContent('doc-5', 'fifth.txt'),
    ];
    const {rerender} = render(
      <DocumentContent
        content={content}
        modalTitleSuffix="conversation message"
      />,
    );

    expect(
      screen.getByRole('listitem', {name: 'first.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'second.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'third.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('listitem', {name: 'fifth.txt'}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Documents (4 of 5)')).toBeInTheDocument();
    expect(screen.queryByText('1 more')).not.toBeInTheDocument();
    rerender(
      <DocumentContent
        content={content}
        modalTitleSuffix="conversation message"
        showAll
      />,
    );
    expect(screen.getAllByRole('listitem')).toHaveLength(5);
    expect(
      screen.getByRole('listitem', {name: 'fifth.txt'}),
    ).toBeInTheDocument();
  });

  it('should open the selected preview with one click and close it again', async () => {
    const doc1 = createDocumentContent('doc-1', 'report.pdf');
    const doc2 = createDocumentContent('doc-2', 'screenshot.png', 'image/png');
    const {user} = render(
      <DocumentContent
        content={[doc1, doc2]}
        modalTitleSuffix="conversation message"
      />,
    );

    await user.click(
      screen.getByRole('button', {name: 'Preview document for screenshot.png'}),
    );

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {
        name: 'Preview: screenshot.png',
      }),
    ).toBeInTheDocument();
    expect(dialog.getByRole('img', {name: 'screenshot.png'})).toHaveAttribute(
      'src',
      expect.stringContaining('doc-2'),
    );
    await user.click(dialog.getByRole('button', {name: 'Close'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should support keyboard preview activation', async () => {
    const doc1 = createDocumentContent('doc-1', 'report.pdf');
    const {user} = render(
      <DocumentContent content={[doc1]} modalTitleSuffix="tool result" />,
    );

    await user.tab();
    await user.keyboard('{Enter}');
    expect(
      screen.getByRole('heading', {name: 'Preview: report.pdf'}),
    ).toBeInTheDocument();
  });

  it('should keep download separate from preview', async () => {
    const {user} = render(
      <DocumentContent
        content={[createDocumentContent('doc-1', 'report.pdf')]}
        modalTitleSuffix="tool result"
      />,
    );
    const download = screen.getByRole('link', {name: 'Download'});
    download.addEventListener('click', (event) => event.preventDefault());
    await user.click(download);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should explain unsupported previews and retain download and size', () => {
    render(
      <DocumentContent
        content={[
          createDocumentContent('doc-1', 'archive.zip', 'application/zip'),
        ]}
        modalTitleSuffix="tool result"
      />,
    );
    expect(
      screen.getByRole('button', {name: 'Preview document for archive.zip'}),
    ).toBeDisabled();
    expect(screen.getByText('Unsupported file type')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'Download'})).toBeInTheDocument();
    expect(screen.getByText('100 B')).toBeInTheDocument();
  });

  it('should disable preview and download for expired documents', () => {
    const doc = createDocumentContent('doc-1', 'expired.pdf');
    doc.documentReference.metadata.expiresAt = '2000-01-01T00:00:00Z';
    render(<DocumentContent content={[doc]} modalTitleSuffix="tool result" />);
    expect(
      screen.getByRole('button', {name: 'Preview document for expired.pdf'}),
    ).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Download'})).toBeDisabled();
    expect(screen.getAllByText('Document has expired')).toHaveLength(2);
  });
});
