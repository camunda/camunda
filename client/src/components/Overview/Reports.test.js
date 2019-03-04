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
  duplicateReport: jest.fn(),
  createProcessReport: jest.fn(),
  showDeleteModalFor: jest.fn()
};

it('should show information about reports', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Report');
});

it('should show a link that goes to the report', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/report/reportID');
});

it('should show no data indicator', () => {
  const node = shallow(<Reports {...props} reports={[]} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a link to the edit mode of the report', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/report/reportID/edit');
});

it('should show invok showDeleteModal when deleting Report', async () => {
  const node = shallow(<Reports {...props} />);

  await node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(props.showDeleteModalFor).toHaveBeenCalled();
});

it('should invok duplicate reports when duplicating reports', () => {
  const node = shallow(<Reports {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateReport).toHaveBeenCalledWith(processReport);
});

it('should display combined tag for combined reports', () => {
  const node = shallow(<Reports {...props} reports={[combinedProcessReport]} />);

  expect(node.find('.dataTitle')).toIncludeText('Combined');
});

it('should display decision tag for decision reports', () => {
  const node = shallow(<Reports {...props} reports={[decisionReport]} />);

  expect(node.find('.dataTitle')).toIncludeText('Decision');
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
