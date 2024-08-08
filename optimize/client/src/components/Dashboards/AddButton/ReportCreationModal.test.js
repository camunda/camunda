/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {createEntity} from 'services';
import {showError} from 'notifications';

import useReportDefinitions from '../useReportDefinitions';

import {ReportCreationModal} from './ReportCreationModal';

jest.mock('notifications', () => ({addNotification: jest.fn(), showError: jest.fn()}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    createEntity: jest.fn().mockReturnValue('123'),
  };
});

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation((data, cb, err) => {
      try {
        return cb(data);
      } catch (e) {
        err?.(e);
      }
    }),
  })),
}));

jest.mock('../useReportDefinitions', () =>
  jest.fn().mockImplementation((existingReport) => ({
    definitions: existingReport?.id ? [{key: 'DefKey'}] : [],
  }))
);

const props = {
  location: 'dashboard/1/edit',
};

it('should load existing report and pass its initial definitions to template modal', () => {
  const node = shallow(<ReportCreationModal existingReport={{id: '123'}} {...props} />);

  runLastEffect();

  expect(useReportDefinitions).toHaveBeenCalledWith({id: '123'}, showError);
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
  expect(spy).toHaveBeenCalledWith({id: '123', type: 'optimize_report'});
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
