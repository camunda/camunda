/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {Filters} from './index';

describe('<Filters />', () => {
  it('should render the correct elements', () => {
    render(<Filters />);

    expect(
      screen.getByRole('heading', {name: /decision/i})
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/version/i)).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: /instance states/i})
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/completed/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/failed/i)).toBeInTheDocument();
    expect(
      screen.getByLabelText(/decision instance id\(s\)/i)
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/process instance id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/evaluation date/i)).toBeInTheDocument();
  });
});
