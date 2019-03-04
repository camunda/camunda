/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import IncidentByError from './IncidentByError';
import * as Styled from './styled';

describe('IncidentByError', () => {
  it('should display the right data', () => {
    const node = shallow(
      <IncidentByError incidentsCount={10} label="someLabel" />
    );

    expect(node.find(Styled.IncidentsCount).text()).toBe('10');
    expect(node.find(Styled.Label).text()).toBe('someLabel');
    expect(node.find(Styled.IncidentBar)).toExist();
    expect(node).toMatchSnapshot();
  });
});
