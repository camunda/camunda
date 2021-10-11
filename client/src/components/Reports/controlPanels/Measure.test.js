/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {createReportUpdate} from 'services';

import Measure from './Measure';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    createReportUpdate: jest.fn(),
  };
});

const props = {
  report: {
    view: {entity: 'processInstance', properties: ['frequency']},
  },
  onChange: jest.fn(),
};

beforeEach(() => {
  props.onChange.mockClear();
  createReportUpdate.mockClear();
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

it('should call updateMeasure with correct payload for deleting a measure', () => {
  const node = shallow(
    <Measure
      {...props}
      report={update(props.report, {view: {properties: {$set: ['frequency', 'duration']}}})}
    />
  );

  node.find('SelectionPreview').first().simulate('click');

  expect(createReportUpdate.mock.calls[0][4].view.properties.$set).toEqual(['duration']);
  expect(props.onChange).toHaveBeenCalled();
});

it('should show Select for single-measure reports', () => {
  const node = shallow(<Measure {...props} />);

  expect(node.find('Select')).toExist();
});

it('should call updateMeasure with correct payload for switching measures', () => {
  const node = shallow(<Measure {...props} />);

  node.find('Select').simulate('change', 'duration');

  expect(createReportUpdate.mock.calls[0][4].view.properties.$set).toEqual(['duration']);
  expect(props.onChange).toHaveBeenCalled();
});
