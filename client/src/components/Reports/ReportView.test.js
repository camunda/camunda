/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportView from './ReportView';

import {checkDeleteConflict, deleteEntity} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    deleteEntity: jest.fn(),
    checkDeleteConflict: jest.fn()
  };
});

jest.mock('./service', () => {
  return {
    remove: jest.fn(),
    isSharingEnabled: jest.fn().mockReturnValue(true)
  };
});

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  };
});

const report = {
  id: '1',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: null,
    configuration: {},
    parameters: {},
    visualization: 'table'
  },
  result: {data: [1, 2, 3]}
};

it('should display the key properties of a report', () => {
  const node = shallow(<ReportView report={report} />);

  node.setState({
    loaded: true,
    report
  });

  expect(node).toIncludeText(report.name);
  expect(node).toIncludeText(report.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find('.edit-button')).toExist();
});

it('should open a deletion modal on delete button click', async () => {
  const node = shallow(<ReportView report={report} />);

  await node.find('.delete-button').prop('onClick')();

  expect(node).toHaveState('confirmModalVisible', true);
});

it('should remove a report when delete is invoked', () => {
  const node = shallow(<ReportView report={report} />);
  node.setState({
    ConfirmModalVisible: true
  });

  node.instance().deleteReport();
  expect(deleteEntity).toHaveBeenCalledWith('report', '1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = shallow(<ReportView report={report} />);

  await node.instance().deleteReport();

  expect(node.find('Redirect')).toExist();
  expect(node.props().to).toEqual('/');
});

it('should contain a ReportRenderer with the report evaluation result', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node).toIncludeText('ReportRenderer');
});

it('should render a sharing popover', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find('.share-button')).toExist();
});

it('should show a download csv button with the correct link', () => {
  const node = shallow(<ReportView report={report} />);
  expect(node.find('.Report__csv-download-button')).toExist();

  const href = node.find('.Report__csv-download-button').props().href;

  expect(href).toContain(report.id);
  expect(href).toContain(report.name);
});

it('should show a download csv button even if the result is 0', () => {
  const node = shallow(<ReportView report={{...report, result: {data: 0}}} />);
  expect(node.find('.Report__csv-download-button')).toExist();
});

it('should reflect excluded columns in the csv download link', () => {
  const newReport = {
    ...report,
    data: {...report.data, configuration: {excludedColumns: ['prop1', 'var__VariableName']}}
  };
  const node = shallow(<ReportView report={newReport} />);
  expect(node.find('.Report__csv-download-button')).toExist();

  const href = node.find('.Report__csv-download-button').props().href;
  expect(href).toContain('?excludedColumns=prop1,variable:VariableName');
});

it('should set conflict state when conflict happens on delete button click', async () => {
  const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];
  checkDeleteConflict.mockReturnValue({
    conflictedItems
  });
  const node = shallow(<ReportView report={report} />);

  await node.find('.delete-button').prop('onClick')();
  expect(node.state().conflict.type).toEqual('delete');
  expect(node.state().conflict.items).toEqual(conflictedItems);
});

it('should render collections dropdown', async () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find('CollectionsDropdown')).toExist();
});

it('should open editCollectionModal when calling openEditCollectionModal', async () => {
  const node = shallow(<ReportView report={report} />);

  node.instance().openEditCollectionModal();

  expect(node.find('EditCollectionModal')).toExist();
});

it('should invoke loadCollections on mount', async () => {
  const node = shallow(<ReportView report={report} />);

  node.instance().loadCollections = jest.fn();
  await node.instance().componentDidMount();

  expect(node.instance().loadCollections).toHaveBeenCalled();
});
