/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DownloadDocumentButton} from '.';
import type {DocumentInfo} from '../documentInfo';

const pdfDocument: DocumentInfo = {
  link: '/v2/documents/pdf',
  fileName: 'report.pdf',
  type: 'pdf',
  contentType: 'application/pdf',
  size: 2048,
  isExpired: false,
};

describe('<DownloadDocumentButton />', () => {
  it('should render a disabled button when document has no link', () => {
    const noLinkDocument: DocumentInfo = {
      link: null,
      fileName: 'no-hash.pdf',
      type: 'pdf',
      contentType: 'application/pdf',
      size: 2048,
      isExpired: false,
    };

    render(
      <DownloadDocumentButton document={noLinkDocument} labelSuffix="myPdf" />,
    );

    const button = screen.getByLabelText('Download document for myPdf');
    expect(button).toBeDisabled();
    expect(button.tagName).not.toBe('A');
  });

  it('should render a download link with correct href and filename', () => {
    render(
      <DownloadDocumentButton document={pdfDocument} labelSuffix="myPdf" />,
    );

    const link = screen.getByLabelText('Download document for myPdf');
    expect(link).toHaveAttribute('href', '/v2/documents/pdf');
    expect(link).toHaveAttribute('download', 'report.pdf');
  });

  it('should render a disabled button with expired tooltip when document is expired', () => {
    const expiredDocument: DocumentInfo = {
      link: '/v2/documents/pdf',
      fileName: 'expired.pdf',
      type: 'pdf',
      contentType: 'application/pdf',
      size: 2048,
      isExpired: true,
    };

    render(
      <DownloadDocumentButton document={expiredDocument} labelSuffix="myPdf" />,
    );

    const button = screen.getByLabelText('Download document for myPdf');
    expect(button).toBeDisabled();
    expect(button.tagName).not.toBe('A');
    expect(screen.getByText('Document has expired')).toBeInTheDocument();
  });
});
