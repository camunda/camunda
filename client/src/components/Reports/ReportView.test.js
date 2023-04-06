/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter, ReportRenderer, InstanceCount, DownloadButton, AlertsDropdown} from 'components';
import {checkDeleteConflict} from 'services';
import {getOptimizeProfile} from 'config';

import {ReportView} from './ReportView';

jest.mock('config', () => ({
  isSharingEnabled: jest.fn().mockReturnValue(true),
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    checkDeleteConflict: jest.fn(),
  };
});

jest.mock('./service', () => {
  return {
    remove: jest.fn(),
  };
});

jest.mock('dates', () => ({
  format: () => 'some date',
}));

const report = {
  id: '1',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  currentUserRole: 'editor',
  data: {
    processDefinitionKey: null,
    configuration: {},
    visualization: 'table',
  },
  result: {measures: [{data: [1, 2, 3]}], instanceCount: 37},
};

it('should display the key properties of a report', () => {
  const node = shallow(<ReportView report={report} />);

  node.setState({
    loaded: true,
    report,
  });

  expect(node.find('EntityName').prop('children')).toBe(report.name);
  expect(node.find(InstanceCount)).toExist();
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find('.edit-button')).toExist();
});

it('should open a deletion modal on delete button click', async () => {
  const node = shallow(<ReportView report={report} />);

  await node.find('.delete-button').prop('onClick')();

  expect(node.find(Deleter).prop('entity')).toBeTruthy();
});

it('should redirect to the report list on report deletion', () => {
  const node = shallow(<ReportView report={report} />);

  node.find(Deleter).prop('onDelete')();

  expect(node.find('Redirect')).toExist();
  expect(node.props().to).toEqual('../../');
});

it('should contain a ReportRenderer with the report evaluation result', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find(ReportRenderer)).toExist();
});

it('should render a sharing popover', async () => {
  const node = await shallow(<ReportView report={report} />);
  await node.update();

  expect(node.find('.share-button')).toExist();
});

it('should provide conflict check method to Deleter', () => {
  const node = shallow(<ReportView report={report} />);

  node.find(Deleter).prop('checkConflicts')({id: '1'});
  expect(checkDeleteConflict).toHaveBeenCalledWith('1', 'report');
});

it('should hide edit/delete if the report current user role is not "editor"', () => {
  const node = shallow(<ReportView report={{...report, currentUserRole: 'viewer'}} />);

  expect(node.find('.delete-button')).not.toExist();
  expect(node.find('.edit-button')).not.toExist();
});

it('should show sharing popover in cloud environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = await shallow(<ReportView report={report} />);

  expect(node.find('Popover.share-button')).toExist();
});

it('should show alert dropdown for number reports', async () => {
  const node = await shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  await node.update();

  expect(node.find(AlertsDropdown)).toExist();
});

it('should hide alert dropdown in ccsm environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = await shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  expect(node.find(AlertsDropdown)).not.toExist();
});

describe('Download CSV', () => {
  it('should show a download csv button with the correct link', () => {
    const node = shallow(<ReportView report={report} />);
    expect(node.find(DownloadButton)).toExist();

    const href = node.find(DownloadButton).props().href;

    expect(href).toContain(report.id);
    expect(href).toContain(report.name);
  });

  it('should show a download csv button even if the result is 0', () => {
    const node = shallow(<ReportView report={{...report, result: {measures: [{data: 0}]}}} />);
    expect(node.find(DownloadButton)).toExist();
  });

  it('should show a download csv button even if the result is null', () => {
    const node = shallow(<ReportView report={{...report, result: {measures: [{data: null}]}}} />);

    expect(node.find(DownloadButton)).toExist();
  });

  it('should not show a download csv button for multi-measure reports', () => {
    const node = shallow(
      <ReportView report={{...report, result: {measures: [{data: 0}, {data: 12}]}}} />
    );
    expect(node.find(DownloadButton)).not.toExist();
  });

  it('should calculate total entries correctly for different report types', () => {
    const node = shallow(
      <ReportView report={{...report, result: {type: 'number', measures: [{data: 12}]}}} />
    );

    expect(node.find(DownloadButton).prop('totalCount')).toBe(1);

    node.setProps({report: {...report, result: {type: 'map', measures: [{data: [{}, {}, {}]}]}}});

    expect(node.find(DownloadButton).prop('totalCount')).toBe(3);

    node.setProps({
      report: {...report, result: {type: 'raw', measures: [{data: [{}]}], instanceCount: 20}},
    });

    expect(node.find(DownloadButton).prop('totalCount')).toBe(20);
  });
});

it('should hide share, edit and delete buttons for instant preview report', () => {
  const node = shallow(
    <ReportView
      report={{
        ...report,
        data: {instantPreviewReport: true},
        result: {type: 'number', measures: [{data: 12}]},
      }}
    />
  );

  expect(node.find('ShareEntity')).not.toExist();
  expect(node.find('.tool-button.edit-button')).not.toExist();
  expect(node.find('.tool-button.delete-button')).not.toExist();
});
