/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import ReportModal from './ReportModal';
import AddButton from './AddButton';
import ReportCreationModal from './ReportCreationModal';

it('should open a modal on click', () => {
  const node = shallow(<AddButton />);

  node.find(Button).simulate('click');

  expect(node.find(ReportModal)).toExist();
});

it('should call the callback when adding a report', () => {
  const spy = jest.fn();
  const node = shallow(<AddButton addReport={spy} />);

  node.find(Button).simulate('click');
  node.find(ReportModal).prop('confirm')({id: 'testReport'});

  expect(spy).toHaveBeenCalledWith({
    configuration: null,
    dimensions: {
      height: 4,
      width: 6,
    },
    position: {
      x: 0,
      y: 0,
    },
    id: 'testReport',
  });
});

it('should call the callback when confirming the report creation modal', () => {
  const spy = jest.fn();
  const node = shallow(<AddButton addReport={spy} />);

  node.find(Button).simulate('click');
  node.find(ReportModal).prop('confirm')({id: 'newReport'});

  node.find(ReportCreationModal).prop('onConfirm')({id: '123'});

  expect(spy).toHaveBeenCalledWith({
    configuration: null,
    dimensions: {
      height: 4,
      width: 6,
    },
    position: {
      x: 0,
      y: 0,
    },
    id: '123',
  });
});
