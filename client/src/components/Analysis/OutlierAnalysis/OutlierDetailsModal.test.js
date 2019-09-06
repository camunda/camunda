/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import OutlierDetailsModal from './OutlierDetailsModal';
import {Button} from 'components';

jest.mock('chart.js');

const selectedNode = {
  name: 'test',
  higherOutlier: {
    count: 4,
    relation: 1.1
  }
};

it('should render a modal with a button group showing duration chart', () => {
  const node = shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(node).toMatchSnapshot();
  expect(node.find('DurationChart')).toExist();
});

it('show total instance count and Variables table when clicking the variable button in the button group', () => {
  const node = shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  node
    .find(Button)
    .at(1)
    .simulate('click');

  expect(node.find('.description')).toExist();
  expect(node.find('VariablesTable')).toExist();
});
