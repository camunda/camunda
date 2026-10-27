/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ToolResultMessage} from './index';

describe('<ToolResultMessage />', () => {
  it('should render nothing without tool call information', () => {
    const {container} = render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[]}
        content={[{contentType: 'TEXT', text: 'Tool output here'}]}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('should render the tool name and a result preview', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        content={[{contentType: 'TEXT', text: 'Tool output here'}]}
      />,
    );

    const message = within(
      screen.getByRole('article', {name: 'Result for "search" tool call'}),
    );

    expect(message.getByRole('heading', {name: 'search'})).toBeInTheDocument();
    expect(message.getByText('Tool output here')).toBeInTheDocument();
  });

  it('should render a compact JSON preview for object content', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        content={[
          {
            contentType: 'OBJECT',
            object: {message: 'Tool output here', hello: 'world'},
          },
        ]}
      />,
    );

    expect(
      screen.getByText('{"message":"Tool output here","hello":"world"}'),
    ).toBeInTheDocument();
  });

  it('should show a fallback message when the result has no content at all', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        content={[]}
      />,
    );

    expect(
      screen.getByText('The tool call did not return content.'),
    ).toBeInTheDocument();
  });

  it('should render document chips and open the document list modal', async () => {
    const {user} = render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        content={[
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              contentHash: 'abc123',
              documentId: 'doc-1',
              storeId: 'default',
              metadata: {
                contentType: 'text/plain',
                fileName: 'report.txt',
                size: 1234,
                expiresAt: null,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
        ]}
      />,
    );

    expect(
      screen.queryByText('The tool call did not return content.'),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'report.txt'}),
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText('View documents'));

    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {name: '1 document in tool result'}),
    ).toBeInTheDocument();
  });

  it('should open the tool result modal when the expand button is clicked', async () => {
    const {user} = render(
      <ToolResultMessage
        availableTools={[
          {name: 'search', description: 'Searches the web.', elementId: null},
        ]}
        toolCalls={[
          {
            toolCallId: '1',
            toolName: 'search',
            elementId: null,
            arguments: {},
          },
        ]}
        content={[]}
      />,
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    const expandButton = screen.getByRole('button', {name: 'Expand'});
    await user.click(expandButton);

    const modal = within(screen.getByRole('dialog'));
    expect(
      modal.getByRole('heading', {name: 'Tool call: search'}),
    ).toBeInTheDocument();
    expect(modal.getByText('Searches the web.')).toBeInTheDocument();
  });
});
