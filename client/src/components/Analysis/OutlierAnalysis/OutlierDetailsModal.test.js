/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import OutlierDetailsModal from './OutlierDetailsModal';

jest.mock('chart.js');

const selectedNode = {
  name: 'test',
  higherOutlier: {
    count: 4,
    relation: 1.1,
  },
};

it('should render a modal with a button group showing duration chart', () => {
  const node = shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(node).toMatchSnapshot();
  expect(node.find('DurationChart')).toExist();
});
