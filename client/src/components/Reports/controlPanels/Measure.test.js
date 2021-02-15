/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import Measure from './Measure';

const props = {
  report: {
    view: {entity: 'processInstance', properties: ['frequency']},
  },
  updateReport: jest.fn(),
};

beforeEach(() => {
  props.updateReport.mockClear();
});

it('should show SelectionPreviews for multi-measure reports', () => {
  const node = shallow(
    <Measure
      {...props}
      report={update(props.report, {view: {properties: {$set: ['frequency', 'duration']}}})}
    />
  );

  expect(node.find('SelectionPreview')).toExist();
  expect(node.find('SelectionPreview').length).toBe(2);
});

it('should call updateReport with correct payload for deleting a measure', () => {
  const node = shallow(
    <Measure
      {...props}
      report={update(props.report, {view: {properties: {$set: ['frequency', 'duration']}}})}
    />
  );

  node.find('SelectionPreview').first().simulate('click');

  expect(props.updateReport).toHaveBeenCalledWith('view', {
    entity: 'processInstance',
    properties: ['duration'],
  });
});

it('should show Select for single-measure reports', () => {
  const node = shallow(<Measure {...props} />);

  expect(node.find('Select')).toExist();
});

it('should call updateReport with correct payload for switching measures', () => {
  const node = shallow(<Measure {...props} />);

  node.find('Select').simulate('change', 'duration');

  expect(props.updateReport).toHaveBeenCalledWith('view', {
    entity: 'processInstance',
    properties: ['duration'],
  });
});
