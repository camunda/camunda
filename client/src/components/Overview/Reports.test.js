import React from 'react';
import {shallow} from 'enzyme';

import {Button, Dropdown} from 'components';

import ReportsWithErrorHandling from './Reports';
import {loadReports, createReport, getReportIcon} from './service';

import {checkDeleteConflict} from 'services';

jest.mock('./service');

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    checkDeleteConflict: jest.fn().mockReturnValue([])
  };
});

const Reports = ReportsWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

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

beforeAll(() => {
  loadReports.mockReturnValue([processReport]);
  getReportIcon.mockReturnValue({Icon: () => {}, label: 'Icon'});
});

it('should show a loading indicator', () => {
  const node = shallow(<Reports {...props} />);

  node.setState({loading: true});

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should load data', () => {
  shallow(<Reports {...props} />);

  expect(loadReports).toHaveBeenCalled();
});

it('should show information about reports', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Report');
});

it('should show a link that goes to the report', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/report/reportID');
});

it('should show no data indicator', () => {
  loadReports.mockReturnValueOnce([]);
  const node = shallow(<Reports {...props} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a link to the edit mode of the report', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/report/reportID/edit');
});

it('should display error messages', () => {
  const node = shallow(<Reports {...props} error="Something went wrong" />);

  expect(node.find('Message')).toBePresent();
});

it('should show create Report buttons', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.createButton')).toBePresent();
});

it('should redirect to new report edit page', async () => {
  createReport.mockReturnValueOnce('newReport');
  const node = shallow(<Reports {...props} />);

  await node
    .find('.createButton')
    .find(Button)
    .simulate('click');

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/report/newReport/edit?new');
});

it('should show confirmation modal when deleting Report', async () => {
  const node = shallow(<Reports {...props} />);

  await node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(node.state('deleting')).toEqual(processReport);
});

it('should duplicate reports', () => {
  createReport.mockClear();

  const node = shallow(<Reports {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(createReport).toHaveBeenCalledWith(false, 'process', {
    ...processReport,
    name: processReport.name + ' - Copy'
  });
});

it('should reload the list after duplication', async () => {
  const node = shallow(<Reports {...props} />);

  loadReports.mockClear();

  await node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(loadReports).toHaveBeenCalled();
});

it('should filter reports with search', () => {
  loadReports.mockReturnValueOnce([
    {
      id: 'reportID',
      name: 'Report 1',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reports: []
    },
    {
      id: 'reportID2',
      name: 'My Report',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reports: []
    }
  ]);

  const node = shallow(<Reports {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: '1'}});

  expect(node.find('li')).toHaveLength(1);
  expect(node.find('.dataTitle')).toIncludeText('Report 1');
});

it('should filter case-insensitive', () => {
  loadReports.mockReturnValueOnce([
    {
      id: 'reportID',
      name: 'Report 1',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reports: []
    },
    {
      id: 'reportID2',
      name: 'My Report',
      lastModifier: 'Admin',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      reports: []
    }
  ]);

  const node = shallow(<Reports {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: 'MY RePoRt'}});

  expect(node.find('li')).toHaveLength(1);
  expect(node.find('.dataTitle')).toIncludeText('My Report');
});

it('should check for deletion conflicts', () => {
  checkDeleteConflict.mockClear();
  const node = shallow(<Reports {...props} />);

  node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(checkDeleteConflict).toHaveBeenCalledWith(processReport.id);
});

it('should have a Dropdown with more creation options', () => {
  const node = shallow(<Reports {...props} />);

  expect(node.find('.createButton').find(Dropdown)).toBePresent();
  expect(node.find('.createButton').find(Dropdown)).toMatchSnapshot();
});

it('should reload the reports after deletion', async () => {
  const node = shallow(<Reports {...props} />);

  loadReports.mockClear();

  await node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  await node.find('ConfirmationModal').prop('onConfirm')();

  expect(loadReports).toHaveBeenCalled();
});

it('should display combined tag for combined reports', () => {
  loadReports.mockReturnValue([combinedProcessReport]);

  const node = shallow(<Reports {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Combined');
});

it('should display dmn tag for decision reports', () => {
  loadReports.mockReturnValue([decisionReport]);

  const node = shallow(<Reports {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('DMN');
});
