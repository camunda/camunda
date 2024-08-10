/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DiagramControls} from './index';

describe('<DiagramControls />', () => {
  it('should render diagram controls', async () => {
    const handleZoomIn = vi.fn(),
      handleZoomOut = vi.fn(),
      handleZoomReset = vi.fn();
    const {user} = render(
      <DiagramControls
        handleZoomIn={handleZoomIn}
        handleZoomOut={handleZoomOut}
        handleZoomReset={handleZoomReset}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Reset diagram zoom'}));

    expect(handleZoomReset).toHaveBeenCalled();
    expect(handleZoomIn).not.toHaveBeenCalled();
    expect(handleZoomOut).not.toHaveBeenCalled();

    handleZoomReset.mockClear();
    await user.click(screen.getByRole('button', {name: 'Zoom in diagram'}));

    expect(handleZoomIn).toHaveBeenCalled();
    expect(handleZoomReset).not.toHaveBeenCalled();
    expect(handleZoomOut).not.toHaveBeenCalled();

    handleZoomIn.mockClear();
    await user.click(screen.getByRole('button', {name: 'Zoom out diagram'}));

    expect(handleZoomOut).toHaveBeenCalled();
    expect(handleZoomReset).not.toHaveBeenCalled();
    expect(handleZoomIn).not.toHaveBeenCalled();
  });
});
