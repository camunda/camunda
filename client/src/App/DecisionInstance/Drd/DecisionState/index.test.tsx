/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {DecisionState} from '.';

describe('<DecisionState />', () => {
  it('should render evaluated state', () => {
    const {container} = render(<div />);

    render(<DecisionState state="EVALUATED" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-completed.svg')
    ).toBeInTheDocument();
  });

  it('should render failed state', () => {
    const {container} = render(<div />);

    render(<DecisionState state="FAILED" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-incident.svg')
    ).toBeInTheDocument();
  });
});
