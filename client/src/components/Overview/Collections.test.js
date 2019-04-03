/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import CollectionsWithStore from './Collections';
import ReportItem from './subComponents/ReportItem';
import DashboardItem from './subComponents/DashboardItem';
const Collections = CollectionsWithStore.WrappedComponent;

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport, dashboard]
  }
};

const collectionWithManyReports = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: new Array(6).fill(processReport)
  }
};

const props = {
  store: {
    updating: null,
    collections: [collection],
    searchQuery: ''
  }
};

it('should show no data indicator', () => {
  const node = shallow(<Collections {...props} store={{collections: [], searchQuery: ''}} />);

  expect(node.find('.collectionBlankSlate')).toBePresent();
});

it('should show the list of entities when entity has open state', () => {
  const node = shallow(<Collections {...props} />);
  node.setState({[collection.id]: true});

  expect(node.find('.entityList')).toBePresent();
});

it('should not show a button to show all reports if the number of reports is less than 5', () => {
  const node = shallow(<Collections {...props} />);

  node.setState({[collection.id]: true});

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all reports if the number of reports is greater than 5', () => {
  const store = {collections: [collectionWithManyReports], searchQuery: ''};
  const node = shallow(<Collections {...props} store={store} />);

  node.setState({[collection.id]: true});

  expect(node.find(Button)).toIncludeText('Show all');
});

it('should show a button to show less reports if the number of reports is greater than 5', () => {
  const store = {collections: [collectionWithManyReports], searchQuery: ''};
  const node = shallow(<Collections {...props} store={store} />);
  node.setState({[collection.id]: true});

  node.find(Button).simulate('click');

  expect(node.find(Button)).toIncludeText('Show less...');
});

it('should render dashboard and report list items', () => {
  const node = shallow(<Collections {...props} />);
  node.setState({[collection.id]: true});

  expect(node.find(ReportItem)).toBePresent();
  expect(node.find(DashboardItem)).toBePresent();
});

it('should show no result found text when no matching reports were found', () => {
  const node = shallow(<Collections {...props} store={{...props.store, searchQuery: 'test'}} />);

  expect(node.find('.empty')).toMatchSnapshot();
});
