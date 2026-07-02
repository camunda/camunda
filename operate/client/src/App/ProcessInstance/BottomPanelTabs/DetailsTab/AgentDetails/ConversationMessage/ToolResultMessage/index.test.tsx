/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ToolResultMessage} from './index';

describe('<ToolResultMessage />', () => {
  it('should render nothing without tool call information', () => {
    const {container} = render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[]}
        result={[{contentType: 'TEXT', text: 'Tool output here'}]}
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
        result={[{contentType: 'TEXT', text: 'Tool output here'}]}
      />,
    );

    expect(screen.getByRole('heading', {name: 'search'})).toBeInTheDocument();
    expect(screen.getByText('Tool output here')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Result for search tool call'),
    ).toBeInTheDocument();
  });

  it('should render a compact JSON preview for object content', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        result={[
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

  it('should show a fallback message when the result has no content', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        result={[]}
      />,
    );

    expect(screen.getByText('Tool result has no content.')).toBeInTheDocument();
  });

  it('should show a fallback message for unsupported content types', () => {
    render(
      <ToolResultMessage
        availableTools={[]}
        toolCalls={[
          {toolCallId: '1', toolName: 'search', elementId: null, arguments: {}},
        ]}
        result={[
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
      screen.getByText('Tool result has no viewable content.'),
    ).toBeInTheDocument();
  });
});
