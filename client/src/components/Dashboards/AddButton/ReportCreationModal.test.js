/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {ReportCreationModal} from './ReportCreationModal';
import {createEntity, loadEntity} from 'services';

jest.mock('notifications', () => ({addNotification: jest.fn()}));
jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadEntity: jest.fn().mockReturnValue({data: {definitions: [{key: 'DefKey'}]}}),
    createEntity: jest.fn().mockReturnValue('123'),
  };
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  location: 'dashboard/1/edit',
};

it('should load existing report and pass its initial definitions to template modal', () => {
  const node = shallow(<ReportCreationModal existingReport={{id: '123'}} {...props} />);

  runLastEffect();

  expect(loadEntity).toHaveBeenCalledWith('report', '123');
  expect(node.find('ReportTemplateModal').prop('initialDefinitions')).toEqual([{key: 'DefKey'}]);
});

it('should create the report when confirming the template modal and invoke onConfirm with its id', () => {
  const spy = jest.fn();
  const node = shallow(<ReportCreationModal {...props} onConfirm={spy} />);

  runLastEffect();

  node.find('ReportTemplateModal').simulate('confirm', {id: 'testReport', name: 'reportName'});

  expect(createEntity).toHaveBeenCalledWith(
    'report/process/single',
    {
      collectionId: null,
      id: 'testReport',
      name: 'reportName',
    },
    'dashboard'
  );
  expect(spy).toHaveBeenCalledWith({id: '123'});
});

it('should invoke onClose when closing the template modal', () => {
  const spy = jest.fn();
  const node = shallow(<ReportCreationModal {...props} onClose={spy} />);

  runLastEffect();

  node.find('ReportTemplateModal').simulate('close');

  expect(spy).toHaveBeenCalled();
});

it('should set initial templates to an empty array when existing report has falsy id and no data', () => {
  const node = shallow(<ReportCreationModal {...props} existingReport={{id: ''}} />);

  runLastEffect();

  expect(node.find('ReportTemplateModal').prop('initialDefinitions')).toEqual([]);
});
