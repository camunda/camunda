import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Collections from './Collections';
jest.mock('./service');

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
  updating: null,
  collections: [collection]
};

it('should show no data indicator', () => {
  const node = shallow(<Collections {...props} collections={[]} />);

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
  const node = shallow(<Collections {...props} collections={[collectionWithManyReports]} />);

  node.setState({[collection.id]: true});

  expect(node.find(Button)).toIncludeText('Show all');
});

it('should show a button to show less reports if the number of reports is greater than 5', () => {
  const node = shallow(<Collections {...props} collections={[collectionWithManyReports]} />);
  node.setState({[collection.id]: true});

  node.find(Button).simulate('click');

  expect(node.find(Button)).toIncludeText('Show less...');
});

it('should render dashboard and report list items', () => {
  const node = shallow(<Collections {...props} />);
  node.setState({[collection.id]: true});

  expect(node.find('ReportItem')).toBePresent();
  expect(node.find('DashboardItem')).toBePresent();
});
