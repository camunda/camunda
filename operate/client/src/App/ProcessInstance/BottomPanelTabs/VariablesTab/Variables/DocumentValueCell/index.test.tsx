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
      document: {fileName: 'photo.png', size: 109748},
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('photo.png')).toBeInTheDocument();
    expect(screen.getByText('110 KB')).toBeInTheDocument();
  });

  it('should render a single document without size', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {fileName: 'report.pdf', size: undefined},
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('report.pdf')).toBeInTheDocument();
    expect(screen.queryByText(/[BKM]B/)).not.toBeInTheDocument();
  });

  it('should middle-truncate a long filename', () => {
    const longName = 'a'.repeat(40) + 'original-middle' + 'z'.repeat(40);
    const result: DocumentParseResult = {
      type: 'single',
      document: {fileName: longName, size: 1000},
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
        {fileName: 'a.pdf', size: 1000},
        {fileName: 'b.pdf', size: 2000},
        {fileName: 'c.pdf', size: 3000},
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
        {fileName: 'a.pdf', size: 1000},
        {fileName: 'b.pdf', size: 2000},
      ],
      isLowerBound: true,
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByText('2+ documents')).toBeInTheDocument();
  });

  it('should set the filename as title attribute for tooltip', () => {
    const result: DocumentParseResult = {
      type: 'single',
      document: {fileName: 'my-important-file.pdf', size: 5000},
    };

    render(<DocumentValueCell result={result} />);

    expect(screen.getByTitle('my-important-file.pdf')).toBeInTheDocument();
  });
});
