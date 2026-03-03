/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DiagramControls} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const MOCK_PROCESS_DEFINITION_KEY = '123';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('<DiagramControls />', () => {
  it('should render diagram controls', async () => {
    const handleZoomIn = vi.fn(),
      handleZoomOut = vi.fn(),
      handleZoomReset = vi.fn(),
      handleFullscreen = vi.fn(),
      handleMinimapToggle = vi.fn();
    const {user} = render(
      <DiagramControls
        handleZoomIn={handleZoomIn}
        handleZoomOut={handleZoomOut}
        handleZoomReset={handleZoomReset}
        handleFullscreen={handleFullscreen}
        isFullscreen={false}
        handleMinimapToggle={handleMinimapToggle}
        isMinimapOpen={false}
        processDefinitionKey={MOCK_PROCESS_DEFINITION_KEY}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      await screen.findByRole('button', {name: 'Reset diagram zoom'}),
    );

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

  it('should render fullscreen button and toggle', async () => {
    const handleFullscreen = vi.fn();
    const {user, rerender} = render(
      <DiagramControls
        handleZoomIn={vi.fn()}
        handleZoomOut={vi.fn()}
        handleZoomReset={vi.fn()}
        handleFullscreen={handleFullscreen}
        isFullscreen={false}
        handleMinimapToggle={vi.fn()}
        isMinimapOpen={false}
      />,
      {wrapper: Wrapper},
    );

    const enterButton = screen.getByRole('button', {
      name: 'Enter fullscreen',
    });
    expect(enterButton).toBeInTheDocument();

    await user.click(enterButton);
    expect(handleFullscreen).toHaveBeenCalledTimes(1);

    rerender(
      <DiagramControls
        handleZoomIn={vi.fn()}
        handleZoomOut={vi.fn()}
        handleZoomReset={vi.fn()}
        handleFullscreen={handleFullscreen}
        isFullscreen={true}
        handleMinimapToggle={vi.fn()}
        isMinimapOpen={false}
      />,
    );

    expect(
      screen.getByRole('button', {name: 'Exit fullscreen'}),
    ).toBeInTheDocument();
  });

  it('should render minimap button and toggle', async () => {
    const handleMinimapToggle = vi.fn();
    const {user, rerender} = render(
      <DiagramControls
        handleZoomIn={vi.fn()}
        handleZoomOut={vi.fn()}
        handleZoomReset={vi.fn()}
        handleFullscreen={vi.fn()}
        isFullscreen={false}
        handleMinimapToggle={handleMinimapToggle}
        isMinimapOpen={false}
      />,
      {wrapper: Wrapper},
    );

    const showButton = screen.getByRole('button', {name: 'Show minimap'});
    expect(showButton).toBeInTheDocument();

    await user.click(showButton);
    expect(handleMinimapToggle).toHaveBeenCalledTimes(1);

    rerender(
      <DiagramControls
        handleZoomIn={vi.fn()}
        handleZoomOut={vi.fn()}
        handleZoomReset={vi.fn()}
        handleFullscreen={vi.fn()}
        isFullscreen={false}
        handleMinimapToggle={handleMinimapToggle}
        isMinimapOpen={true}
      />,
    );

    expect(
      screen.getByRole('button', {name: 'Hide minimap'}),
    ).toBeInTheDocument();
  });

  it('should not render download button', async () => {
    render(
      <DiagramControls
        handleZoomIn={vi.fn()}
        handleZoomOut={vi.fn()}
        handleZoomReset={vi.fn()}
        handleFullscreen={vi.fn()}
        isFullscreen={false}
        handleMinimapToggle={vi.fn()}
        isMinimapOpen={false}
      />,
      {wrapper: Wrapper},
    );

    expect(
      screen.queryByRole('button', {name: 'Download XML'}),
    ).not.toBeInTheDocument();
  });
});
