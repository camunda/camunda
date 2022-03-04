/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import OutlierDetailsModal from './OutlierDetailsModal';

const selectedNode = {
  name: 'test',
  higherOutlier: {
    count: 4,
    relation: 1.1,
  },
  data: [
    {key: '1', outlier: false},
    {key: '2', outlier: true},
  ],
};

it('should pass outlier data to DurationChart and VariablesTable', () => {
  const node = shallow(<OutlierDetailsModal selectedNode={selectedNode} />);

  expect(node.find('DurationChart').prop('data')).toEqual(selectedNode.data);
  expect(node.find('DurationChart').prop('colors')).toEqual(['#eeeeee', '#1991c8']);
  expect(node.find('VariablesTable').prop('selectedNode')).toEqual(selectedNode);
});
