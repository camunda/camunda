/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen, within} from 'modules/testing-library';
import {createVariable, searchResult} from 'modules/testUtils';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {getWrapper, mockProcessInstance} from './mocks';

const makeDocumentRef = (overrides: Record<string, unknown> = {}) => ({
  'camunda.document.type': 'camunda',
  documentId: 'doc-abc',
  storeId: 'in-memory',
  contentHash: 'sha256-xyz',
  metadata: {
    fileName: 'test-document.pdf',
    contentType: 'application/pdf',
    size: 1024,
  },
  ...overrides,
});

describe('VariablesTab document variables', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should render a download button for variables with single documents', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({
          name: 'myDocument',
          value: JSON.stringify(makeDocumentRef()),
        }),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-myDocument'));
    const downloadButton = variableRow.getByLabelText(
      'Download document for variable myDocument',
    );
    expect(downloadButton).toHaveAttribute(
      'href',
      '/v2/documents/doc-abc?storeId=in-memory&contentHash=sha256-xyz',
    );
    expect(downloadButton).toHaveAttribute('download', 'test-document.pdf');
  });

  it('should not render a download button for variables with multiple documents', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({
          name: 'myDocumentList',
          value: JSON.stringify([
            makeDocumentRef({documentId: 'doc-1'}),
            makeDocumentRef({documentId: 'doc-2'}),
          ]),
        }),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-myDocumentList'));
    expect(
      variableRow.queryByLabelText(/download document for variable/i),
    ).not.toBeInTheDocument();
  });

  it('should render an enabled preview button for image documents', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({
          name: 'myImage',
          value: JSON.stringify(
            makeDocumentRef({
              metadata: {
                fileName: 'photo.png',
                contentType: 'image/png',
              },
            }),
          ),
        }),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-myImage'));
    const previewButton = variableRow.getByLabelText(
      'Preview document for variable myImage',
    );
    expect(previewButton).toBeEnabled();
  });

  it('should render a disabled preview button for unsupported document types', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({
          name: 'myDocument',
          value: JSON.stringify(
            makeDocumentRef({
              metadata: {
                fileName: 'document.docx',
                contentType: 'application/docx',
              },
            }),
          ),
        }),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-myDocument'));
    const previewButton = variableRow.getByLabelText(
      'Preview document for variable myDocument',
    );
    expect(previewButton).toBeDisabled();
  });

  it('should not render a preview button for variables with multiple documents', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({
          name: 'myDocumentList',
          value: JSON.stringify([
            makeDocumentRef({documentId: 'doc-1'}),
            makeDocumentRef({documentId: 'doc-2'}),
          ]),
        }),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-myDocumentList'));
    expect(
      variableRow.queryByLabelText(/preview document for variable/i),
    ).not.toBeInTheDocument();
  });

  it('should not render a download button for regular variables', async () => {
    mockSearchVariables().withSuccess(searchResult([createVariable()]));

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const variableRow = within(screen.getByTestId('variable-testVariableName'));
    expect(
      variableRow.queryByLabelText(/download document for variable/i),
    ).not.toBeInTheDocument();
  });
});
