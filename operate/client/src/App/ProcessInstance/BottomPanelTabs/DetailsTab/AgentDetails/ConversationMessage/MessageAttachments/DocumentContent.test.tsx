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

function createDocumentContent(id: string, fileName: string): ContentItem {
  return {
    contentType: 'DOCUMENT',
    documentReference: {
      'camunda.document.type': 'camunda',
      contentHash: id,
      documentId: id,
      storeId: 'default',
      metadata: {
        contentType: 'text/plain',
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
      <DocumentContent content={[{contentType: 'TEXT', text: 'hello'}]} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('should render a chip for each document entry', () => {
    const doc1 = createDocumentContent('doc-1', 'report.txt');
    const doc2 = createDocumentContent('doc-2', 'screenshot.png');
    render(<DocumentContent content={[doc1, doc2]} />);

    expect(
      screen.getByRole('listitem', {name: 'report.txt'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'screenshot.png'}),
    ).toBeInTheDocument();
  });

  it('should truncate long document filenames in chips', () => {
    const doc1 = createDocumentContent(
      'doc-1',
      'a-very-long-document-name.txt',
    );
    render(<DocumentContent content={[doc1]} />);

    const chip = screen.getByRole('listitem', {
      name: 'a-very-long-document-name.txt',
    });
    expect(chip).toHaveTextContent('a-very-lo…-name.txt');
  });

  it('should show at most 3 document chips and indicate the remaining count', () => {
    const content = [
      createDocumentContent('doc-1', 'first.txt'),
      createDocumentContent('doc-2', 'second.txt'),
      createDocumentContent('doc-3', 'third.txt'),
      createDocumentContent('doc-4', 'fourth.txt'),
    ];
    render(<DocumentContent content={content} />);

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
      screen.queryByRole('listitem', {name: 'fourth.txt'}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('1 more')).toBeInTheDocument();
  });

  it('should open the document list modal when the view-documents button is clicked', async () => {
    const doc1 = createDocumentContent('doc-1', 'report.txt');
    const doc2 = createDocumentContent('doc-2', 'screenshot.png');
    const {user} = render(<DocumentContent content={[doc1, doc2]} />);

    await user.click(screen.getByLabelText('View documents'));

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {
        name: '2 documents in conversation message',
      }),
    ).toBeInTheDocument();
  });
});
