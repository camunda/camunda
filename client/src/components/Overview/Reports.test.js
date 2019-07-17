/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import ReportsWithStore from './Reports';

const Reports = ReportsWithStore.WrappedComponent;

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

const reports = [
  processReport,
  processReport,
  combinedProcessReport,
  decisionReport,
  processReport,
  processReport
];

const props = {
  store: {
    reports: [processReport],
    collections: [collection],
    searchQuery: ''
  },
  duplicateEntity: jest.fn(),
  showDeleteModalFor: jest.fn(),
  entitiesCollections: {reportID: [collection]},
  renderCollectionsDropdown: jest.fn()
};

it('should show no data indicator', () => {
  const node = shallow(<Reports {...props} store={{reports: [], searchQuery: ''}} />);

  expect(node.find('NoEntities')).toExist();
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('ToggleButton')).toExist();
});

it('should hide the list of entities when clicking the collapse buttons', () => {
  const node = shallow(<Reports {...props} />);

  const button = node
    .find('ToggleButton')
    .dive()
    .find('.ToggleCollapse');

  button.simulate('click');

  expect(node.find('.entityList')).not.toExist();
});

it('should not show a button to show all entities if the number of entities is less than 5', () => {
  const node = shallow(<Reports {...props} />);

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Reports {...props} store={{reports, searchQuery: ''}} />);

  expect(node).toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Reports {...props} store={{reports, searchQuery: ''}} />);

  const button = node.find(Button).filter({variant: 'link'});

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});

it('should show no result found text when no matching reports were found', () => {
  const node = shallow(<Reports {...props} store={{...props.store, searchQuery: 'test'}} />);

  expect(node.find('.empty')).toMatchSnapshot();
});
