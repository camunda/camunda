/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ModificationBadgeOverlay} from './index';
import {render, screen} from 'modules/testing-library';

describe('ModificationBadgeOverlay', () => {
  const createOverlayContainer = () => {
    const container = document.createElement('div');
    document.body.append(container);
    return container;
  };

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('should display cancelled tokens', async () => {
    render(
      <ModificationBadgeOverlay
        container={createOverlayContainer()}
        newTokenCount={0}
        cancelledTokenCount={3}
      />,
    );

    expect(screen.getByTestId('badge-minus-icon')).toBeInTheDocument();
    expect(screen.getByText(/3/)).toBeInTheDocument();
    expect(screen.queryByTestId('badge-plus-icon')).not.toBeInTheDocument();
  });

  it('should display new tokens', async () => {
    render(
      <ModificationBadgeOverlay
        container={createOverlayContainer()}
        newTokenCount={2}
        cancelledTokenCount={0}
      />,
    );

    expect(screen.getByTestId('badge-plus-icon')).toBeInTheDocument();
    expect(screen.getByText(/2/)).toBeInTheDocument();
    expect(screen.queryByTestId('badge-minus-icon')).not.toBeInTheDocument();
  });

  it('should display new tokens and cancelled tokens', async () => {
    render(
      <ModificationBadgeOverlay
        container={createOverlayContainer()}
        newTokenCount={2}
        cancelledTokenCount={3}
      />,
    );

    expect(screen.getByTestId('badge-minus-icon')).toBeInTheDocument();
    expect(screen.getByTestId('badge-plus-icon')).toBeInTheDocument();
    expect(screen.getByText(/2/)).toBeInTheDocument();
    expect(screen.getByText(/3/)).toBeInTheDocument();
  });
});
