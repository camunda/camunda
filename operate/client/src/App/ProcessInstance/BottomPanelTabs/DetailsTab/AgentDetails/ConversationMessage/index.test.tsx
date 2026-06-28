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

  it('should render markdown in the expand modal', async () => {
    const {user} = render(
      <ConversationMessage
        actor="USER"
        content={[{contentType: 'TEXT', text: 'Use `npm install` to set up'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    const code = within(modal).getByText('npm install');
    expect(code.tagName).toBe('CODE');
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

  it('should show Preview and Source tabs in the modal', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **world**'}]}
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

  it('should default to preview mode in the modal', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **bold**'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByRole('tab', {name: 'Preview', selected: true}),
    ).toBeInTheDocument();
    expect(within(modal).getByText('bold')).toBeInTheDocument();
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
});
