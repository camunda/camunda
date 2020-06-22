/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsBanner} from './index';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {EXPAND_STATE} from 'modules/constants';
import {MemoryRouter} from 'react-router-dom';
import {render, screen} from '@testing-library/react';
import PropTypes from 'prop-types';

const mockProps = {
  count: 1,
  onClick: jest.fn(),
  isArrowFlipped: false,
  expandState: 'DEFAULT',
};

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useParams: () => ({
    id: 1,
  }),
}));

const Wrapper = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', () => {
    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There is 1 Incident in Instance 1.')
    ).toBeInTheDocument();
  });

  it('should not display incidents banner if panel is collapsed', () => {
    render(
      <IncidentsBanner {...mockProps} expandState={EXPAND_STATE.COLLAPSED} />,
      {wrapper: Wrapper}
    );

    expect(
      screen.queryByText('There is 1 Incident in Instance 1.')
    ).not.toBeInTheDocument();
  });

  it('should show the right text for more than 1 incident', () => {
    render(<IncidentsBanner {...mockProps} count={2} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There are 2 Incidents in Instance 1.')
    ).toBeInTheDocument();
  });
});
