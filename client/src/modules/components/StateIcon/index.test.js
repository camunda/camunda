/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {STATE} from 'modules/constants';
import ThemedStateIcon from './index';
import {Colors} from 'modules/theme';

const {WrappedComponent: StateIcon} = ThemedStateIcon;

describe('StateIcon', () => {
  it('should render Incident Icon', () => {
    render(<StateIcon theme="dark" state={STATE.INCIDENT} />);

    expect(screen.getByTestId(`${STATE.INCIDENT}-icon`)).toBeInTheDocument();
    expect(screen.getByTestId(`${STATE.INCIDENT}-icon`)).toHaveStyleRule(
      'color',
      Colors.incidentsAndErrors
    );
  });

  it('should render Active Icon', () => {
    render(<StateIcon state={STATE.ACTIVE} theme="dark" />);

    expect(screen.getByTestId(`${STATE.ACTIVE}-icon`)).toBeInTheDocument();
    expect(screen.getByTestId(`${STATE.ACTIVE}-icon`)).toHaveStyleRule(
      'color',
      Colors.allIsWell
    );
  });

  it('should render Completed Icon', () => {
    render(<StateIcon state={STATE.COMPLETED} theme="dark" />);

    expect(screen.getByTestId(`${STATE.COMPLETED}-icon`)).toBeInTheDocument();
    expect(screen.getByTestId(`${STATE.COMPLETED}-icon`)).toHaveStyleRule(
      'color',
      '#ffffff'
    );
    expect(screen.getByTestId(`${STATE.COMPLETED}-icon`)).toHaveStyleRule(
      'opacity',
      '0.46'
    );
  });

  it('should render Canceled Icon', () => {
    render(<StateIcon state={STATE.CANCELED} theme="dark" />);

    expect(screen.getByTestId(`${STATE.CANCELED}-icon`)).toBeInTheDocument();
    expect(screen.getByTestId(`${STATE.CANCELED}-icon`)).toHaveStyleRule(
      'color',
      '#ffffff'
    );
    expect(screen.getByTestId(`${STATE.CANCELED}-icon`)).toHaveStyleRule(
      'opacity',
      '0.81'
    );
  });

  it('should render an Alias Icon if no suitable icon is available', () => {
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();
    render(<StateIcon state={'SomeUnknownState'} theme="dark" />);
    expect(screen.getByTestId('SomeUnknownState-icon')).toBeInTheDocument();

    expect(screen.getByTestId('SomeUnknownState-icon')).toHaveStyleRule(
      'background',
      '#ffffff'
    );
    expect(screen.getByTestId('SomeUnknownState-icon')).toHaveStyleRule(
      'opacity',
      '0.46'
    );
    expect(screen.getByTestId('SomeUnknownState-icon')).toHaveStyleRule(
      'height',
      '15px'
    );
    expect(screen.getByTestId('SomeUnknownState-icon')).toHaveStyleRule(
      'width',
      '15px'
    );
    expect(screen.getByTestId('SomeUnknownState-icon')).toHaveStyleRule(
      'border-radius',
      '50%'
    );
    global.console.error = originalConsoleError;
  });
});
