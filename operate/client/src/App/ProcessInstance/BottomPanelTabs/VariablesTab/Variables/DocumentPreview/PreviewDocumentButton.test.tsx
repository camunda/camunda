/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {PreviewDocumentButton} from './PreviewDocumentButton';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';

const imageDocument: DocumentInfo = {
  link: '/v2/documents/img',
  fileName: 'photo.png',
  type: 'image',
  size: 1024,
};

const pdfDocument: DocumentInfo = {
  link: '/v2/documents/pdf',
  fileName: 'report.pdf',
  type: 'unknown',
  size: 2048,
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

  it('should render a disabled preview button for unsupported types', () => {
    render(
      <PreviewDocumentButton document={pdfDocument} variableName="myPdf" />,
    );

    const button = screen.getByLabelText('Preview document for variable myPdf');
    expect(button).toBeDisabled();
    expect(
      screen.getByText('Preview not available for this document type'),
    ).toBeInTheDocument();
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
});
