/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import IncidentByWorkflow from './IncidentByWorkflow';
import * as Styled from './styled';

describe('IncidentByWorkflow', () => {
  it('should display the right data', () => {
    const node = shallow(
      <IncidentByWorkflow
        incidentsCount={10}
        label="someLabel"
        activeCount={8}
      />
    );
    const BarNode = node.find(Styled.IncidentsBar);
    expect(node.find(Styled.IncidentsCount).text()).toBe('10');
    expect(node.find(Styled.Label).text()).toBe('someLabel');
    expect(node.find(Styled.ActiveCount).text()).toBe('8');
    expect(BarNode).toExist();
    expect(BarNode.find(Styled.IncidentsBar)).toExist();
    expect(BarNode.find(Styled.IncidentsBar).props().style.width).toContain(
      (10 * 100) / 18
    );
    expect(node).toMatchSnapshot();
  });
});
