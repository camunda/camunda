/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {currentTheme} from 'modules/stores/currentTheme';
import {StateOverlay} from '.';

describe('StateOverlay', () => {
  let container: HTMLElement;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.append(container);
  });

  afterEach(() => {
    currentTheme.reset();
  });

  it('should render active badge', () => {
    render(<StateOverlay state="ACTIVE" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-active.svg')
    ).toBeInTheDocument();

    currentTheme.toggle();

    expect(
      screen.getByText('diagram-badge-single-instance-active.svg')
    ).toBeInTheDocument();
  });

  it('should render incident badge', () => {
    render(<StateOverlay state="INCIDENT" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-incident.svg')
    ).toBeInTheDocument();

    currentTheme.toggle();

    expect(
      screen.getByText('diagram-badge-single-instance-incident.svg')
    ).toBeInTheDocument();
  });

  it('should render completed badge', () => {
    render(<StateOverlay state="COMPLETED" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-completed.svg')
    ).toBeInTheDocument();

    currentTheme.toggle();

    expect(
      screen.getByText('diagram-badge-single-instance-completed.svg')
    ).toBeInTheDocument();
  });

  it('should render terminated badge', () => {
    render(<StateOverlay state="TERMINATED" container={container} />);

    expect(
      screen.getByText('diagram-badge-single-instance-canceled.svg')
    ).toBeInTheDocument();

    currentTheme.toggle();

    expect(
      screen.getByText('diagram-badge-single-instance-canceled.svg')
    ).toBeInTheDocument();
  });
});
