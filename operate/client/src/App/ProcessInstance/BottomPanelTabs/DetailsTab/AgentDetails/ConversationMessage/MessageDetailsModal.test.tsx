/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {MessageDetailsModal} from './MessageDetailsModal';

vi.unmock('react-markdown');

describe('<MessageDetailsModal />', () => {
  it('should render markdown in preview mode by default', () => {
    render(
      <MessageDetailsModal
        title="Assistant message"
        language="markdown"
        content="Use `npm install` to set up"
        onClose={vi.fn()}
      />,
    );

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByRole('tab', {name: 'Preview', selected: true}),
    ).toBeInTheDocument();
    const code = within(modal).getByText('npm install');
    expect(code.tagName).toBe('CODE');
  });

  it('should show Preview and Source tabs for markdown content', () => {
    render(
      <MessageDetailsModal
        title="Assistant message"
        language="markdown"
        content="Hello **world**"
        onClose={vi.fn()}
      />,
    );

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByRole('tab', {name: 'Preview'}),
    ).toBeInTheDocument();
    expect(
      within(modal).getByRole('tab', {name: 'Source'}),
    ).toBeInTheDocument();
  });

  it('should show the raw source when the Source tab is selected', async () => {
    const {user} = render(
      <MessageDetailsModal
        title="Assistant message"
        language="markdown"
        content="Hello **world**"
        onClose={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('tab', {name: 'Source'}));

    expect(screen.getByTestId('monaco-editor')).toHaveValue('Hello **world**');
  });

  it('should render source only without tabs for json content', () => {
    render(
      <MessageDetailsModal
        title="Object details"
        language="json"
        content='{"foo": "bar"}'
        onClose={vi.fn()}
      />,
    );

    expect(
      screen.queryByRole('tab', {name: 'Preview'}),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', {name: 'Source'})).not.toBeInTheDocument();
    expect(screen.getByTestId('monaco-editor')).toHaveValue('{"foo": "bar"}');
  });

  it('should call onClose when the close button is clicked', async () => {
    const onClose = vi.fn();
    const {user} = render(
      <MessageDetailsModal
        title="Assistant message"
        language="markdown"
        content="Hello"
        onClose={onClose}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Close'}));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
