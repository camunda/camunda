/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, fireEvent} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockDownloadDocument} from 'modules/mocks/api/v2/documents/downloadDocument';
import {PreviewDocumentButton} from './PreviewDocumentButton';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import {tracking} from 'modules/tracking';

const createWrapper = (): React.FC<{children?: React.ReactNode}> => {
  const client = getMockQueryClient();
  return ({children}) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
};

const imageDocument: DocumentInfo = {
  link: '/v2/documents/img',
  fileName: 'photo.png',
  type: 'image',
  contentType: 'image/png',
  size: 1024,
};

const pdfDocument: DocumentInfo = {
  link: '/v2/documents/pdf',
  fileName: 'report.pdf',
  type: 'pdf',
  contentType: 'application/pdf',
  size: 2048,
};

const jsonDocument: DocumentInfo = {
  link: '/v2/documents/json',
  fileName: 'data.json',
  type: 'json',
  contentType: 'application/json',
  size: 256,
};

const unknownDocument: DocumentInfo = {
  link: '/v2/documents/archive',
  fileName: 'archive.zip',
  type: 'unknown',
  contentType: 'application/zip',
  size: 4096,
};

describe('<PreviewDocumentButton />', () => {
  it('should render an enabled preview button for image documents', () => {
    render(
      <PreviewDocumentButton document={imageDocument} variableName="myImage" />,
    );

    const button = screen.getByLabelText(
      'Preview document for variable myImage',
    );
    expect(button).toBeEnabled();
  });

  it('should render an enabled preview button for pdf documents', () => {
    render(
      <PreviewDocumentButton document={pdfDocument} variableName="myPdf" />,
    );

    const button = screen.getByLabelText('Preview document for variable myPdf');
    expect(button).toBeEnabled();
  });

  it('should render an enabled preview button for json documents', () => {
    render(
      <PreviewDocumentButton document={jsonDocument} variableName="myJson" />,
      {wrapper: createWrapper()},
    );

    const button = screen.getByLabelText(
      'Preview document for variable myJson',
    );
    expect(button).toBeEnabled();
  });

  it('should render a disabled preview button for unsupported types', () => {
    render(
      <PreviewDocumentButton
        document={unknownDocument}
        variableName="myArchive"
      />,
    );

    const button = screen.getByLabelText(
      'Preview document for variable myArchive',
    );
    expect(button).toBeDisabled();
    expect(
      screen.getByText('Preview not available for this document type'),
    ).toBeInTheDocument();
  });

  it('should open the modal with the pdf when clicked', async () => {
    const {user} = render(
      <PreviewDocumentButton document={pdfDocument} variableName="myPdf" />,
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myPdf'),
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    const iframe = screen.getByTitle('report.pdf');
    expect(iframe.tagName).toBe('IFRAME');
    expect(iframe).toHaveAttribute('src', '/v2/documents/pdf');
  });

  it('should open the modal with the image when clicked', async () => {
    const {user} = render(
      <PreviewDocumentButton document={imageDocument} variableName="myImage" />,
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myImage'),
    );

    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    const image = screen.getByRole('img', {name: 'photo.png'});
    expect(image).toHaveAttribute('src', '/v2/documents/img');
  });

  it('should open the modal and render pretty-printed JSON when clicked', async () => {
    mockDownloadDocument().withSuccess('{"foo":"bar","nested":{"baz":1}}');

    const {user} = render(
      <PreviewDocumentButton document={jsonDocument} variableName="myJson" />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myJson'),
    );

    const editor = await screen.findByTestId('monaco-editor');
    expect(editor).toHaveValue(
      '{\n\t"foo": "bar",\n\t"nested": {\n\t\t"baz": 1\n\t}\n}',
    );
  });

  it('should show an error notification when the JSON document fails to load', async () => {
    mockDownloadDocument().withServerError();

    const {user} = render(
      <PreviewDocumentButton document={jsonDocument} variableName="myJson" />,
      {wrapper: createWrapper()},
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myJson'),
    );

    expect(
      await screen.findByText(
        `Failed to load JSON preview for "${jsonDocument.fileName}".`,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('monaco-editor')).not.toBeInTheDocument();
  });

  it('should show the file name as the modal heading', async () => {
    const {user} = render(
      <PreviewDocumentButton document={imageDocument} variableName="myImage" />,
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myImage'),
    );

    expect(
      screen.getByRole('heading', {name: 'Preview: photo.png'}),
    ).toBeInTheDocument();
  });

  it('should render a disabled preview button when document has no link', () => {
    const noLinkDocument: DocumentInfo = {
      link: null,
      fileName: 'no-hash.pdf',
      type: 'pdf',
      contentType: 'application/pdf',
      size: 1024,
    };

    render(
      <PreviewDocumentButton document={noLinkDocument} variableName="myDoc" />,
    );

    const button = screen.getByLabelText('Preview document for variable myDoc');
    expect(button).toBeDisabled();
    expect(
      screen.getByText('Preview not available for this document'),
    ).toBeInTheDocument();
  });

  it('should track "document-previewed" events', async () => {
    const trackSpy = vi.spyOn(tracking, 'track');
    const {user} = render(
      <PreviewDocumentButton document={imageDocument} variableName="myImage" />,
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myImage'),
    );

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'document-previewed',
      documentType: 'image',
      contentType: 'image/png',
      size: 1024,
    });
  });

  it('should show an error notification when the image fails to load', async () => {
    const {user} = render(
      <PreviewDocumentButton document={imageDocument} variableName="myImage" />,
    );

    await user.click(
      screen.getByLabelText('Preview document for variable myImage'),
    );

    const image = screen.getByRole('img', {name: 'photo.png'});
    fireEvent.error(image);

    expect(
      await screen.findByText(
        `Failed to load image preview for "${imageDocument.fileName}".`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('img', {name: 'photo.png'}),
    ).not.toBeInTheDocument();
  });
});
