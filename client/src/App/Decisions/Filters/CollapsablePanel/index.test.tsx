/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {CollapsablePanel} from './index';

const header = 'this is a header';
const content = 'this is the content';

describe('<CollapsablePanel />', () => {
  it('should collapse and expand content', () => {
    render(<CollapsablePanel header={header}>{content}</CollapsablePanel>);

    expect(screen.getByText(header)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /collapse/i,
      })
    ).toBeInTheDocument();
    expect(screen.getByText(content)).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: /collapse/i,
      })
    );

    expect(screen.queryByText(content)).not.toBeInTheDocument();
    expect(screen.getByText(header)).toBeInTheDocument();

    userEvent.click(screen.getByText(header));

    expect(screen.getByText(header)).toBeInTheDocument();
    expect(screen.getByText(content)).toBeInTheDocument();
  });
});
