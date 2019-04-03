/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import ReportItemWithStore from './ReportItem';
import {getReportIcon} from '../service';
import {formatters} from 'services';

const ReportItem = ReportItemWithStore.WrappedComponent;

jest.mock('../service');
jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {...rest, formatters: {getHighlightedText: jest.fn()}};
});

beforeAll(() => {
  getReportIcon.mockReturnValue({Icon: () => {}, label: 'Icon'});
});

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport]
  }
};

const combinedProcessReport = {
  id: 'reportID',
  name: 'Multiple reports',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: true
};

const decisionReport = {
  id: 'reportID',
  name: 'Some Decision Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'decision'
};

const props = {
  store: {searchQuery: '', collections: []},
  report: processReport,
  entitiesCollections: {reportID: [{id: 'aCollectionId'}]},
  toggleEntityCollection: jest.fn(),
  setCollectionToUpdate: jest.fn(),
  duplicateEntity: jest.fn(),
  showDeleteModalFor: jest.fn(),
  renderCollectionsDropdown: jest.fn()
};

it('should show information about reports', () => {
  formatters.getHighlightedText.mockReturnValue('Some Report');
  const node = shallow(<ReportItem {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Report');
});

it('should show a link that goes to the report', () => {
  const node = shallow(<ReportItem {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/report/reportID');
});

it('should contain a link to the edit mode of the report', () => {
  const node = shallow(<ReportItem {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/report/reportID/edit');
});

it('should show invok showDeleteModal when deleting Report', async () => {
  const node = shallow(<ReportItem {...props} />);

  await node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(props.showDeleteModalFor).toHaveBeenCalled();
});

it('should invok duplicate reports when duplicating reports', () => {
  const node = shallow(<ReportItem {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateEntity).toHaveBeenCalledWith('report', processReport, undefined);
});

it('should invok duplicate report when clicking duplicate report button with the report and the parent collection', () => {
  const node = shallow(<ReportItem {...props} collection={collection} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateEntity).toHaveBeenCalledWith('report', processReport, collection);
});

it('should display combined tag for combined reports', () => {
  const node = shallow(<ReportItem {...props} report={combinedProcessReport} />);

  expect(node.find('.dataTitle')).toIncludeText('Combined');
});

it('should display decision tag for decision reports', () => {
  const node = shallow(<ReportItem {...props} report={decisionReport} />);

  expect(node.find('.dataTitle')).toIncludeText('Decision');
});

it('should invoke getHighlightedText with the name and the searchQuery', () => {
  shallow(<ReportItem {...props} store={{searchQuery: 'some'}} />);

  expect(formatters.getHighlightedText).toHaveBeenCalledWith('Some Report', 'some');
});
