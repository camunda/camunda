/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ToolResultModal} from './index';
import type {ContentItem} from '../getRenderableResult';

describe('<ToolResultModal />', () => {
  it('should show and copy every recorded content block without preview transformations', async () => {
    const input = {
      document: '<doc id="source-document" />',
      duplicateConfirmed: true,
    };
    const content: ContentItem[] = [
      {contentType: 'TEXT', text: 'Original <doc id="source-document" />'},
      {
        contentType: 'OBJECT',
        object: {
          duplicateConfirmed: true,
          evidence: {kind: 'sandbox', operation: 'sandbox_bash'},
        },
      },
      {contentType: 'TEXT', text: 'Second text block'},
    ];
    const {user} = render(
      <ToolResultModal
        toolName="reconcile"
        description={null}
        input={input}
        content={content}
        onClose={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('tab', {name: 'Source'}));

    const inputColumn = within(screen.getByTestId('tool-call-input'));
    const outputColumn = within(screen.getByTestId('tool-call-output'));
    expect(await inputColumn.findByRole('textbox')).toHaveValue(
      JSON.stringify(input, null, 2),
    );
    expect(await outputColumn.findByRole('textbox')).toHaveValue(
      JSON.stringify(content, null, 2),
    );
    await user.click(
      outputColumn.getByRole('button', {name: 'Copy to clipboard'}),
    );
    expect(await navigator.clipboard.readText()).toBe(
      JSON.stringify(content, null, 2),
    );
    await user.click(screen.getByRole('tab', {name: 'Preview'}));
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(outputColumn.getByText('Original')).toBeInTheDocument();
  });

  it.each([null, {}])(
    'should preserve empty input %j and output in source',
    async (input) => {
      const {user} = render(
        <ToolResultModal
          toolName="empty"
          description={null}
          input={input}
          content={[]}
          onClose={vi.fn()}
        />,
      );

      await user.click(screen.getByRole('tab', {name: 'Source'}));

      expect(
        await within(screen.getByTestId('tool-call-input')).findByRole(
          'textbox',
        ),
      ).toHaveValue(JSON.stringify(input, null, 2));
      expect(
        await within(screen.getByTestId('tool-call-output')).findByRole(
          'textbox',
        ),
      ).toHaveValue('[]');
    },
  );

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
    expect(screen.queryByTestId('tool-call-documents')).not.toBeInTheDocument();
    expect(screen.queryByText('Documents')).not.toBeInTheDocument();
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
    expect(screen.getByTestId('tool-call-documents')).toBeInTheDocument();
    await user.click(
      screen.getByRole('button', {name: 'Preview document for report.pdf'}),
    );
    expect(
      screen.getByRole('heading', {name: 'Preview: report.pdf'}),
    ).toBeInTheDocument();
    await user.click(
      within(
        screen.getByRole('dialog', {name: 'Preview: report.pdf'}),
      ).getByRole('button', {name: 'Close'}),
    );
    await user.click(screen.getByRole('tab', {name: 'Source'}));
    expect(await outputColumn.findByRole('textbox')).toHaveDisplayValue(
      /"documentId": "doc-1"/,
    );
    expect(
      screen.queryByRole('listitem', {name: 'report.pdf'}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('tool-call-documents')).not.toBeInTheDocument();
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
