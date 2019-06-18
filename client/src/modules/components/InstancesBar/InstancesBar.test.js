/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import InstancesBar from './InstancesBar';
import * as Styled from './styled';

describe('InstancesBar', () => {
  it('should display the right data', () => {
    const node = shallow(
      <InstancesBar
        incidentsCount={10}
        label="someLabel"
        activeCount={8}
        barHeight={5}
        size="small"
      />
    );

    expect(node.find(Styled.IncidentsCount).text()).toBe('10');
    expect(node.find(Styled.Label).text()).toBe('someLabel');
    expect(node.find(Styled.ActiveCount).text()).toBe('8');
    expect(node.find(Styled.Bar)).toExist();
    expect(node.find(Styled.IncidentsBar).props().style.width).toContain(
      (10 * 100) / 18
    );
    expect(node).toMatchSnapshot();
  });
});
