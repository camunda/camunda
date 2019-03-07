/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import * as Styled from './styled';
import IncidentsBar from './IncidentsBar';

const mockProps = {
  id: '1',
  count: 1,
  onClick: jest.fn(),
  isArrowFlipped: false
};

describe('IncidentsBar', () => {
  it('should show the right text for 1 incident', () => {
    const node = mount(<IncidentsBar {...mockProps} />);
    expect(node.text()).toContain('There is 1 Incident in Instance 1.');
  });

  it('should show the right text for more than 1 incident', () => {
    const mockProps2 = {
      ...mockProps,
      count: 2
    };
    const node = mount(<IncidentsBar {...mockProps2} />);

    expect(node.text()).toContain('There are 2 Incidents in Instance 1.');
  });

  it('should call the onClick handler', () => {
    const node = mount(<IncidentsBar {...mockProps} />);
    node.simulate('click');

    expect(mockProps.onClick).toHaveBeenCalledTimes(1);
  });

  it('should contain an arrow', () => {
    const node = mount(<IncidentsBar {...mockProps} />);

    expect(node.find(Styled.Arrow).props().isFlipped).toBe(false);

    node.setProps({isArrowFlipped: true});
    node.update();

    expect(node.find(Styled.Arrow).props().isFlipped).toBe(true);
  });
});
