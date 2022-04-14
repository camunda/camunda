/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import DiagramControls from './index';

describe('<DiagramControls />', () => {
  it('should render diagram controls', async () => {
    const handleZoomIn = jest.fn(),
      handleZoomOut = jest.fn(),
      handleZoomReset = jest.fn();
    const {user} = render(
      <DiagramControls
        handleZoomIn={handleZoomIn}
        handleZoomOut={handleZoomOut}
        handleZoomReset={handleZoomReset}
      />,
      {wrapper: ThemeProvider}
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
