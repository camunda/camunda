/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ToolResultModal} from './index';

describe('<ToolResultModal />', () => {
  it('should render the tool name as the modal heading and tool description', () => {
    render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={null}
        content={[]}
        onClose={vi.fn()}
      />,
    );

    const modal = within(screen.getByRole('dialog'));
    expect(
      modal.getByRole('heading', {name: 'Tool call: search'}),
    ).toBeInTheDocument();
    expect(modal.getByText('Searches the web.')).toBeInTheDocument();
  });

  it('should render the input as fields and the output as text', async () => {
    render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={{query: 'camunda'}}
        content={[{contentType: 'TEXT', text: 'Tool output here'}]}
        onClose={vi.fn()}
      />,
    );

    const input = within(screen.getByTestId('tool-call-input'));
    const output = within(screen.getByTestId('tool-call-output'));
    expect(input.getByText('Query')).toBeInTheDocument();
    expect(input.getByText('camunda')).toBeInTheDocument();
    expect(output.getByText('Tool output here')).toBeInTheDocument();
    expect(screen.queryByTestId('monaco-editor')).not.toBeInTheDocument();
  });

  it('should render object results as output', async () => {
    render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={{query: 'camunda'}}
        content={[
          {contentType: 'OBJECT', object: {message: 'Tool output here'}},
        ]}
        onClose={vi.fn()}
      />,
    );

    const output = within(screen.getByTestId('tool-call-output'));
    expect(output.getByText('Message')).toBeInTheDocument();
    expect(output.getByText('Tool output here')).toBeInTheDocument();
    expect(output.queryByText(/[{}"]/)).not.toBeInTheDocument();
  });

  it('should not show an input editor when no input was provided', async () => {
    render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={null}
        content={[]}
        onClose={vi.fn()}
      />,
    );

    const inputColumn = within(screen.getByTestId('tool-call-input'));
    expect(
      screen.getByText('Tool called without input arguments.'),
    ).toBeInTheDocument();
    expect(inputColumn.queryByTestId('monaco-editor')).not.toBeInTheDocument();
    expect(
      inputColumn.queryByRole('button', {name: 'Copy to clipboard'}),
    ).not.toBeInTheDocument();
  });

  it('should not show an output editor when no renderable content was provided', async () => {
    const {user} = render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={{query: 'camunda'}}
        content={[
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              contentHash: 'abc123',
              documentId: 'doc-1',
              storeId: 'default',
              metadata: {
                contentType: 'application/pdf',
                fileName: 'report.pdf',
                size: 1234,
                expiresAt: null,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
        ]}
        onClose={vi.fn()}
      />,
    );

    const outputColumn = within(screen.getByTestId('tool-call-output'));
    expect(
      screen.getByText('The tool call did not return content.'),
    ).toBeInTheDocument();
    expect(outputColumn.queryByTestId('monaco-editor')).not.toBeInTheDocument();
    expect(
      outputColumn.queryByRole('button', {name: 'Copy to clipboard'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'report.pdf'}),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {name: 'Preview document for report.pdf'}),
    );
    expect(
      screen.getByRole('heading', {name: 'Preview: report.pdf'}),
    ).toBeInTheDocument();
  });

  it('should call onClose when the close button is clicked', async () => {
    const onClose = vi.fn();
    const {user} = render(
      <ToolResultModal
        toolName="search"
        description="Searches the web."
        input={{query: 'camunda'}}
        content={[{contentType: 'TEXT', text: 'Tool output here'}]}
        onClose={onClose}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Close'}));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
