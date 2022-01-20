/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Diagram} from './index';

describe('<Diagram />', () => {
  it('should render diagram controls', async () => {
    render(<Diagram xml={'<bpmn:definitions/>'} />, {wrapper: ThemeProvider});

    expect(await screen.findByText('Diagram mock')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: 'Reset diagram zoom'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom in diagram'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom out diagram'})
    ).toBeInTheDocument();
  });
});
