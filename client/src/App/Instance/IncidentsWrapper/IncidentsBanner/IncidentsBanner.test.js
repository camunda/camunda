/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import IncidentsBanner from './IncidentsBanner';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {EXPAND_STATE} from 'modules/constants';

const mockProps = {
  id: '1',
  count: 1,
  onClick: jest.fn(),
  isArrowFlipped: false,
  expandState: 'DEFAULT'
};

const mountComponent = mockProps =>
  mount(
    <ThemeProvider>
      <IncidentsBanner {...mockProps} />
    </ThemeProvider>
  );

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', () => {
    const node = mountComponent(mockProps);
    expect(node.contains(IncidentsBanner)).toBe(true);
  });

  it('should not display incidents banner if panel is collapsed', () => {
    const node = mountComponent({
      ...mockProps,
      expandState: EXPAND_STATE.COLLAPSED
    });
    expect(node.contains(IncidentsBanner)).toBe(false);
  });

  it('should show the right text for 1 incident', () => {
    const node = mountComponent(mockProps);
    expect(node.text()).toContain('There is 1 Incident in Instance 1.');
  });

  it('should show the right text for more than 1 incident', () => {
    const mockProps2 = {
      ...mockProps,
      count: 2
    };
    const node = mountComponent(mockProps2);

    expect(node.text()).toContain('There are 2 Incidents in Instance 1.');
  });

  it('should call the onClick handler', () => {
    const node = mountComponent(mockProps);
    node.simulate('click');

    expect(mockProps.onClick).toHaveBeenCalledTimes(1);
  });
});
