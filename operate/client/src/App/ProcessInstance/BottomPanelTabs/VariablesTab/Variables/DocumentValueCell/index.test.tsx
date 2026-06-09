/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DocumentValueCell} from './index';
import type {DocumentParseResult} from './parseDocumentVariable';

describe('<DocumentValueCell />', () => {
  it('should render a single document with filename and size', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {
        link: '/v2/documents/doc',
        fileName: 'photo.png',
        type: 'image',
        contentType: 'image/png',
        size: 112640,
        isExpired: false,
      },
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('photo.png')).toBeInTheDocument();
    expect(screen.getByText('110 KiB')).toBeInTheDocument();
  });

  it('should middle-truncate a long filename', () => {
    const longName = 'a'.repeat(40) + 'original-middle' + 'z'.repeat(40);
    const result: DocumentParseResult = {
      type: 'single',
      document: {
        link: '/v2/documents/doc',
        fileName: longName,
        type: 'unknown',
        contentType: 'application/octet-stream',
        size: 1000,
        isExpired: false,
      },
    };

    render(<DocumentValueCell result={result} />);

    const displayed = screen.getByTestId('document-value-cell').textContent!;
    expect(displayed).toContain('\u2026');
    expect(displayed).not.toContain('original-middle');
  });

  it('should render a document list count', () => {
    const result: DocumentParseResult = {
      type: 'list',
      documents: [
        {
          link: '/v2/documents/doc-1',
          fileName: 'a.pdf',
          type: 'unknown',
          contentType: 'application/octet-stream',
          size: 1000,
          isExpired: false,
        },
        {
          link: '/v2/documents/doc-2',
          fileName: 'b.pdf',
          type: 'unknown',
          contentType: 'application/octet-stream',
          size: 2000,
          isExpired: false,
        },
        {
          link: '/v2/documents/doc-3',
          fileName: 'c.pdf',
          type: 'unknown',
          contentType: 'application/octet-stream',
          size: 3000,
          isExpired: false,
        },
      ],
      isLowerBound: false,
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('3 documents')).toBeInTheDocument();
  });

  it('should render lower-bound count with plus sign', () => {
    const result: DocumentParseResult = {
      type: 'list',
      documents: [
        {
          link: '/v2/documents/doc-1',
          fileName: 'a.pdf',
          type: 'unknown',
          contentType: 'application/octet-stream',
          size: 1000,
          isExpired: false,
        },
        {
          link: '/v2/documents/doc-2',
          fileName: 'b.pdf',
          type: 'unknown',
          contentType: 'application/octet-stream',
          size: 2000,
          isExpired: false,
        },
      ],
      isLowerBound: true,
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('2+ documents')).toBeInTheDocument();
  });

  it('should set the filename as title attribute for tooltip', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {
        link: '/v2/documents/doc',
        fileName: 'my-important-file.pdf',
        type: 'unknown',
        contentType: 'application/octet-stream',
        size: 5000,
        isExpired: false,
      },
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByTitle('my-important-file.pdf')).toBeInTheDocument();
  });

  it('should render an expired tag for expired documents', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {
        link: '/v2/documents/doc',
        fileName: 'old.pdf',
        type: 'pdf',
        contentType: 'application/pdf',
        size: 1024,
        isExpired: true,
      },
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('Expired')).toBeInTheDocument();
  });

  it('should not render an expired tag for non-expired documents', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {
        link: '/v2/documents/doc',
        fileName: 'current.pdf',
        type: 'pdf',
        contentType: 'application/pdf',
        size: 1024,
        isExpired: false,
      },
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.queryByText('Expired')).not.toBeInTheDocument();
  });
});
