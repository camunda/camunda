import React from 'react';
import {shallow} from 'enzyme';

import ReportOverview from './ReportOverview';
import {remove} from './service';

import {checkDeleteConflict} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
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
  result: [1, 2, 3]
};

it('should display the key properties of a report', () => {
  const node = shallow(<ReportOverview report={report} />);

  node.setState({
    loaded: true,
    report
  });

  expect(node).toIncludeText(report.name);
  expect(node).toIncludeText(report.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<ReportOverview report={report} />);

  expect(node.find('.edit-button')).toBePresent();
});

it('should open a deletion modal on delete button click', async () => {
  const node = shallow(<ReportOverview report={report} />);

  await node.find('.delete-button').prop('onClick')();

  expect(node).toHaveState('confirmModalVisible', true);
});

it('should remove a report when delete is invoked', () => {
  const node = shallow(<ReportOverview report={report} />);
  node.setState({
    ConfirmModalVisible: true
  });

  node.instance().deleteReport();
  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = shallow(<ReportOverview report={report} />);

  await node.instance().deleteReport();

  expect(node.find('Redirect')).toBePresent();
  expect(node.props().to).toEqual('/reports');
});

it('should contain a ReportView with the report evaluation result', () => {
  const node = shallow(<ReportOverview report={report} />);

  expect(node).toIncludeText('ReportView');
});

it('should render a sharing popover', () => {
  const node = shallow(<ReportOverview report={report} />);

  expect(node.find('.share-button')).toBePresent();
});

it('should show a download csv button with the correct link', () => {
  const node = shallow(<ReportOverview report={report} />);
  expect(node.find('.Report__csv-download-button')).toBePresent();

  const href = node.find('.Report__csv-download-button').props().href;

  expect(href).toContain(report.id);
  expect(href).toContain(report.name);
});

it('should reflect excluded columns in the csv download link', () => {
  const newReport = {
    ...report,
    data: {...report.data, configuration: {excludedColumns: ['prop1', 'var__VariableName']}}
  };
  const node = shallow(<ReportOverview report={newReport} />);
  expect(node.find('.Report__csv-download-button')).toBePresent();

  const href = node.find('.Report__csv-download-button').props().href;
  expect(href).toContain('?excludedColumns=prop1,variable:VariableName');
});

it('should set conflict state when conflict happens on delete button click', async () => {
  const conflictedItems = [{id: '1', name: 'alert', type: 'Alert'}];
  checkDeleteConflict.mockReturnValue({
    conflictedItems
  });
  const node = shallow(<ReportOverview report={report} />);

  await node.find('.delete-button').prop('onClick')();
  expect(node.state().conflict.type).toEqual('Delete');
  expect(node.state().conflict.items).toEqual(conflictedItems);
});
