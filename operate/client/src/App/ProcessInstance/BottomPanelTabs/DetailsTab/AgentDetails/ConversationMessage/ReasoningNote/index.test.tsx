/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {currentTheme} from 'modules/stores/currentTheme';
import {render, screen, within} from 'modules/testing-library';
import {ReasoningNote} from './index';

const REASONING =
  'Coverage must be established before estimating a settlement.\n' +
  'The complete evidence remains visible. '.repeat(30);

describe('<ReasoningNote />', () => {
  it('should render full reasoning without interactive chrome', () => {
    render(<ReasoningNote reasoning={REASONING} />);

    const note = screen.getByRole('region', {name: 'Thinking'});
    const reasoning = within(note).getByText(
      /Coverage must be established before estimating a settlement/,
    );
    expect(reasoning.textContent).toBe(REASONING);
    expect(screen.getByText('Thinking')).toHaveClass('text-xs');
    expect(reasoning).toHaveClass(
      'text-sm',
      'italic',
      'text-neutral-foreground-subtle',
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('should use the current Operate theme', () => {
    currentTheme.changeTheme('dark');

    render(<ReasoningNote reasoning={REASONING} />);

    expect(
      screen.getByRole('region', {name: 'Thinking'}).closest('[data-c4-scope]'),
    ).toHaveClass('dark');
  });
});
