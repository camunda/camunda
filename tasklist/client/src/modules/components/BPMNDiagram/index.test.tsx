/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {BPMNDiagram} from './index';

describe.skip('<BPMNDiagram />', () => {
  it('should render diagram controls', async () => {
    render(<BPMNDiagram xml={'<bpmn:definitions/>'} />);

    expect(await screen.findByText('Diagram mock')).toBeInTheDocument();

    expect(
      await screen.findByRole('button', {name: 'Reset diagram zoom'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom in diagram'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Zoom out diagram'}),
    ).toBeInTheDocument();
  });
});
