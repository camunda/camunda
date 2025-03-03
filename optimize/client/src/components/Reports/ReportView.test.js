/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import {useHistory, useLocation} from 'react-router-dom';

import {Deleter, ReportRenderer, InstanceCount, DownloadButton, AlertsDropdown} from 'components';
import {checkDeleteConflict} from 'services';
import {useUiConfig} from 'hooks';

import ReportView from './ReportView';

jest.mock('hooks', () => ({
  useUiConfig: jest.fn().mockReturnValue({sharingEnabled: true, userSearchAvailable: true}),
  useUser: jest.fn().mockReturnValue({user: {}}),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    checkDeleteConflict: jest.fn(),
  };
});

jest.mock('./service', () => {
  const rest = jest.requireActual('./service');
  return {
    ...rest,
    remove: jest.fn(),
  };
});

jest.mock('dates', () => ({
  format: () => 'some date',
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: '/report'}),
  useHistory: jest.fn().mockReturnValue({push: jest.fn()}),
}));

const report = {
  id: '1',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  currentUserRole: 'editor',
  data: {
    processDefinitionKey: null,
    configuration: {},
    visualization: 'table',
    view: {properties: ['frequency']},
  },
  result: {measures: [{data: [1, 2, 3]}], instanceCount: 37},
};

it('should display the key properties of a report', () => {
  const node = shallow(<ReportView report={report} />);

  runLastEffect();

  expect(node.find('EntityName').prop('name')).toBe(report.name);
  expect(node.find(InstanceCount)).toExist();
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find({to: 'edit'})).toExist();
});

it('should open a deletion modal on delete button click', () => {
  const node = shallow(<ReportView report={report} />);

  node.find({iconDescription: 'Delete'}).prop('onClick')();

  expect(node.find(Deleter).prop('entity')).toBeTruthy();
});

it('should redirect to the report list on report deletion', () => {
  const spy = {push: jest.fn()};
  useHistory.mockReturnValue(spy);
  const node = shallow(<ReportView report={report} />);

  node.find(Deleter).prop('onDelete')();

  expect(spy.push).toHaveBeenCalledWith('../../');
});

it('should contain a ReportRenderer with the report evaluation result', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find(ReportRenderer)).toExist();
});

it('should render sharing options', async () => {
  const node = shallow(<ReportView report={report} />);

  await runLastEffect();
  await node.update();

  expect(node.find('ShareEntity')).toExist();
});

it('should hide Sharing options if sharing is disabled', () => {
  useUiConfig.mockReturnValueOnce({sharingEnabled: false, userSearchAvailable: true});
  const node = shallow(<ReportView report={report} />);

  runLastEffect();

  expect(node.find('ShareEntity')).not.toExist();
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

it('should show alert dropdown for number reports', async () => {
  const node = shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  runLastEffect();
  await flushPromises();

  expect(node.find(AlertsDropdown)).toExist();
});

it('should hide alert dropdown if usersearch is not available', () => {
  useUiConfig.mockReturnValueOnce({sharingEnabled: true, userSearchAvailable: false});
  const node = shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  expect(node.find(AlertsDropdown)).not.toExist();
});

describe('Download CSV', () => {
  const rawDataReport = {
    ...report,
    data: {
      ...report.data,
      view: {properties: ['rawData']},
    },
  };

  it('should show a download csv button with the correct link for raw data reports', () => {
    const node = shallow(<ReportView report={rawDataReport} />);
    expect(node.find(DownloadButton)).toExist();

    const href = node.find(DownloadButton).props().href;

    expect(href).toContain(report.id);
    expect(href).toContain(report.name);
  });

  it('should show a download csv button even if the result is 0', () => {
    const node = shallow(
      <ReportView report={{...rawDataReport, result: {measures: [{data: 0}]}}} />
    );
    expect(node.find(DownloadButton)).toExist();
  });

  it('should show a download csv button even if the result is null', () => {
    const node = shallow(
      <ReportView report={{...rawDataReport, result: {measures: [{data: null}]}}} />
    );

    expect(node.find(DownloadButton)).toExist();
  });

  it('should not show a download csv button for multi-measure reports', () => {
    const node = shallow(<ReportView report={report} />);
    expect(node.find(DownloadButton)).not.toExist();
  });

  it('should not show a download csv button if the visualization is number', () => {
    const node = shallow(
      <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
    );

    expect(node.find(DownloadButton)).not.toExist();
  });

  it('should calculate total entries correctly for raw data report', () => {
    const node = shallow(<ReportView report={rawDataReport} />);

    expect(node.find(DownloadButton).prop('totalCount')).toBe(37);
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

it('should hide bottom raw data panel for table reports', () => {
  const node = shallow(<ReportView report={report} />);

  expect(node.find('.bottomPanel')).not.toExist();
});

it('should hide bottom raw data panel for processes page reports', () => {
  useLocation.mockReturnValueOnce({pathname: '/processes/report'});

  const node = shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  expect(node.find('.bottomPanel')).not.toExist();
});

it('should hide bottom raw data panel for empty reports', () => {
  const node = shallow(
    <ReportView
      report={{...report, result: undefined, data: {...report.data, visualization: 'number'}}}
    />
  );

  expect(node.find('.bottomPanel')).not.toExist();
});

it('should hide report content when expanding bottom panel', () => {
  const node = shallow(
    <ReportView report={{...report, data: {...report.data, visualization: 'number'}}} />
  );

  node.find('CollapsibleContainer').dive().find('.expandButton').simulate('click');
  expect(node.find('.Report__content').hasClass('hidden')).toBe(true);
});
