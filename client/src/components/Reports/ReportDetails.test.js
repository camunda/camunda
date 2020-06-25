/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {loadTenants} from './service';

import {Button} from 'components';
import {ReportDetails} from './ReportDetails';
import RawDataModal from './RawDataModal';
import DiagramModal from './DiagramModal';

jest.mock('react', () => {
  const outstandingEffects = [];
  return {
    ...jest.requireActual('react'),
    useEffect: (fn) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()();
      }
    },
  };
});
jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([
    {id: 'sales', name: 'Sales'},
    {id: 'consulting', name: 'Consulting'},
    {id: 'snacks', name: 'Snacks'},
  ]),
}));

const props = {
  report: {
    data: {
      processDefinitionKey: 'aKey',
      processDefinitionName: 'aName',
      processDefinitionVersions: ['2', '1'],
      tenantIds: ['sales', 'consulting'],
      view: {property: 'frequency', entity: 'processInstance'},
      groupBy: {type: 'startDate', value: {unit: 'automatic'}},
    },
    reportType: 'process',
    owner: 'Test Person',
    lastModified: '2020-06-23T09:32:48.938+0200',
    lastModifier: 'Test Person',
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should show relevant information', () => {
  const node = shallow(<ReportDetails {...props} />);
  runLastEffect();

  expect(node).toIncludeText('aName');
  expect(node).toIncludeText('2, 1');
  expect(node).toIncludeText('Sales, Consulting');
  expect(node).toIncludeText('Process Instance Count by Start Date');
  expect(node).toIncludeText('Test Person');
  expect(node).toIncludeText('Jun 23');
});

it('should not show tenant section if there is only one tenant', () => {
  const oneTenant = update(props.report, {data: {tenantIds: {$set: ['sales']}}});
  loadTenants.mockReturnValueOnce([{id: 'sales', name: 'Sales'}]);

  const node = shallow(<ReportDetails {...props} report={oneTenant} />);
  runLastEffect();

  expect(node).not.toIncludeText('Sales');
  expect(node).not.toIncludeText('Tenant');
});

it('should have special handling for variable views', () => {
  const variableReport = update(props.report, {
    data: {
      view: {$set: {entity: 'variable', property: {name: 'x', type: 'Integer'}}},
      groupBy: {$set: {type: 'none', value: null}},
    },
  });

  const node = shallow(<ReportDetails {...props} report={variableReport} />);
  runLastEffect();

  expect(node).toIncludeText('Variable x');
  expect(node.find('dd.nowrap')).toExist();
});

it('should open raw data modal which button is clicked', () => {
  const node = shallow(<ReportDetails {...props} />);
  runLastEffect();

  node.find('.modalsButtons').find(Button).at(0).simulate('click');

  expect(node.find(RawDataModal)).toExist();
});

it('should open diagram modal when button is clicked', () => {
  const node = shallow(<ReportDetails {...props} />);
  runLastEffect();

  node.find('.modalsButtons').find(Button).at(1).simulate('click');

  expect(node.find(DiagramModal)).toExist();
});
