/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {ConversationMessage} from './index';

vi.unmock('react-markdown');

describe('<ConversationMessage />', () => {
  it('should open a markdown preview modal when expand is clicked', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **world**'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    expect(modal).toBeInTheDocument();
    expect(within(modal).getByText('Assistant message')).toBeInTheDocument();
    expect(within(modal).getByText('world')).toBeInTheDocument();
  });

  it('should close the modal when close button is clicked', async () => {
    const {user} = render(
      <ConversationMessage
        actor="SYSTEM"
        content={[{contentType: 'TEXT', text: 'System message'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Close'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should show correct modal title for each actor', async () => {
    const {user, rerender} = render(
      <ConversationMessage
        actor="SYSTEM"
        content={[{contentType: 'TEXT', text: 'prompt'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByText('System prompt'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Close'}));

    rerender(
      <ConversationMessage
        actor="USER"
        content={[{contentType: 'TEXT', text: 'msg'}]}
      />,
    );
    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByText('User message'),
    ).toBeInTheDocument();
  });

  it('should reset to preview mode when modal is reopened', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **world**'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    await user.click(screen.getByRole('tab', {name: 'Source'}));
    await user.click(screen.getByRole('button', {name: 'Close'}));

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByRole('tab', {
        name: 'Preview',
        selected: true,
      }),
    ).toBeInTheDocument();
  });

  it('should show Preview and Source tabs for plain text messages too', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Just plain text'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByRole('tab', {name: 'Preview'}),
    ).toBeInTheDocument();
    expect(
      within(modal).getByRole('tab', {name: 'Source'}),
    ).toBeInTheDocument();
  });

  it('should render assistant reasoning content as structured content', () => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              provider: 'openai',
              payload: {
                id: 'rs_937932',
                type: 'reasoning',
                encrypted_content: 'Opaque provider payload',
              },
              text: 'Check the policy before estimating the claim.',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText('Thinking')).toBeVisible();
    expect(
      screen.getByText('Check the policy before estimating the claim.'),
    ).toBeVisible();
    expect(
      screen.queryByText('Opaque provider payload'),
    ).not.toBeInTheDocument();
  });

  it('should not interpret provider-specific reasoning payloads', () => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              provider: 'anthropic',
              payload: {
                type: 'thinking',
                signature: 'opaque',
              },
              text: 'Use the available evidence.',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText('Thinking')).toBeVisible();
    expect(screen.getByText('Use the available evidence.')).toBeVisible();
  });

  it('should preserve the generic object fallback for unrecognized content', () => {
    const {rerender} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              type: 'reasoning',
              text: 'Some structured content',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText(/"text": "Some structured content"/)).toBeVisible();
    expect(screen.queryByText('Thinking')).not.toBeInTheDocument();

    rerender(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              provider: 'anthropic',
              payload: {type: 'redacted_thinking', data: 'opaque'},
              text: '   ',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText(/"provider": "anthropic"/)).toBeVisible();
    expect(screen.queryByText('Thinking')).not.toBeInTheDocument();
  });

  it('should leave thinking XML in ordinary assistant text unstructured', () => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'TEXT',
            text: '<thinking><context>Temporary context</context><reflection>Temporary reasoning</reflection></thinking>',
          },
        ]}
      />,
    );

    expect(screen.queryByText('Thinking')).not.toBeInTheDocument();
    expect(screen.queryByText('Reasoning')).not.toBeInTheDocument();
  });
});
