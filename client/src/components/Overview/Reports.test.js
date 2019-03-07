import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Reports from './Reports';
import {getReportIcon} from './service';

jest.mock('./service');

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

const reports = [
  processReport,
  processReport,
  combinedProcessReport,
  decisionReport,
  processReport,
  processReport
];

const props = {
  reports: [processReport],
  collections: [collection],
  duplicateEntity: jest.fn(),
  createProcessReport: jest.fn(),
  showDeleteModalFor: jest.fn(),
  entitiesCollections: {reportID: [collection]},
  renderCollectionsDropdown: jest.fn()
};

it('should show no data indicator', () => {
  const node = shallow(<Reports {...props} reports={[]} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('ToggleButton')).toBePresent();
});

it('should hide the list of entities when clicking the collapse buttons', () => {
  const node = shallow(<Reports {...props} />);

  const button = node
    .find('ToggleButton')
    .dive()
    .find('.ToggleCollapse');

  button.simulate('click');

  expect(node.find('.entityList')).not.toBePresent();
});

it('should not show a button to show all entities if the number of entities is less than 5', () => {
  const node = shallow(<Reports {...props} />);

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Reports {...props} reports={reports} />);

  expect(node).toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Reports {...props} reports={reports} />);

  const button = node.find(Button).filter('[type="link"]');

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});
