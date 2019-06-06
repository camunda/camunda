/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import EmptyIncidents from './EmptyPanel';

describe('EmptyPanel', () => {
  it('should display a warning message', () => {
    const node = shallow(<EmptyIncidents label="someLabel" type="warning" />);
    expect(node).toMatchSnapshot();
  });

  it('should display a success message', () => {
    const node = shallow(<EmptyIncidents label="someLabel" type="info" />);
    expect(node).toMatchSnapshot();
  });
});
