import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Collections from './Collections';
import {getReportIcon} from './service';
jest.mock('./service');

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

beforeAll(() => {
  getReportIcon.mockReturnValue({Icon: () => {}, label: 'Icon'});
});

const props = {
  updating: null,
  collections: [collection],
  updateOrCreateCollection: jest.fn(),
  setCollectionToUpdate: jest.fn(),
  showDeleteModalFor: jest.fn(),
  duplicateReport: jest.fn()
};

it('should show information about collections', () => {
  const node = shallow(<Collections {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('aCollectionName');
});

it('should show no data indicator', () => {
  const node = shallow(<Collections {...props} collections={[]} />);

  expect(node.find('.collectionBlankSlate')).toBePresent();
});

it('should invok setCollectionToUpdate on updating a collection', () => {
  const node = shallow(<Collections {...props} />);
  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click');

  expect(props.setCollectionToUpdate).toHaveBeenCalledWith({
    id: 'aCollectionId',
    name: 'aCollectionName'
  });
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<Collections {...props} />);

  expect(node.find('.ToggleCollapse')).toBePresent();
});

it('should show the list of entities when clicking the collapse buttons', () => {
  const node = shallow(<Collections {...props} />);

  node.find('.ToggleCollapse').simulate('click');

  expect(node.find('.entityList')).toBePresent();
});

it('should not show a button to show all reports if the number of reports is less than 5', () => {
  const node = shallow(<Collections {...props} />);

  node.find('.ToggleCollapse').simulate('click');

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all report if the number of reports is greater than 5', () => {
  const node = shallow(<Collections {...props} collections={[collectionWithManyReports]} />);

  node.find('.ToggleCollapse').simulate('click');

  expect(node).toIncludeText('Show all');
});

it('should show a button to show less reports if the number of reports is greater than 5', () => {
  const node = shallow(<Collections {...props} collections={[collectionWithManyReports]} />);
  node.find('.ToggleCollapse').simulate('click');

  const button = node.find(Button).filter('[type="link"]');

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});

it('should show a link for reports inside collection', () => {
  const node = shallow(<Collections {...props} />);
  node.find('.ToggleCollapse').simulate('click');

  expect(node.find('li > Link').prop('to')).toBe('/report/reportID');
});

it('should invok duplicate report when clicking duplicate report button', () => {
  const node = shallow(<Collections {...props} />);
  node.find('.ToggleCollapse').simulate('click');

  node
    .find('.operations')
    .at(1)
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateReport).toHaveBeenCalledWith(processReport);
});
