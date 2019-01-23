import React from 'react';
import {shallow} from 'enzyme';

import HomeWithErrorHandling from './Home';
import {loadDashboards, loadReports, getReportIcon} from './service';

jest.mock('./service');

const Home = HomeWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
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
  loadDashboards.mockReturnValue([dashboard]);
  loadReports.mockReturnValue([processReport]);
  getReportIcon.mockReturnValue({Icon: () => {}, label: 'Icon'});
});

it('should show a loading indicator', () => {
  const node = shallow(<Home {...props} />);

  node.setState({loadingDashboards: true});

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should load data', () => {
  shallow(<Home {...props} />);

  expect(loadReports).toHaveBeenCalled();
  expect(loadDashboards).toHaveBeenCalled();
});

it('should show information about dashboards and reports', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.dashboards .dataTitle')).toIncludeText('Some Dashboard');
  expect(node.find('.reports .dataTitle')).toIncludeText('Some Report');
});

it('should show a link that goes to the entity', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.dashboards li > Link').prop('to')).toBe('/dashboard/dashboardID');
  expect(node.find('.reports li > Link').prop('to')).toBe('/report/reportID');
});

it('should display only five reports', () => {
  loadReports.mockReturnValueOnce([
    processReport,
    processReport,
    processReport,
    processReport,
    processReport,
    processReport,
    processReport,
    processReport
  ]);
  const node = shallow(<Home {...props} />);

  expect(node.find('.reports li')).toHaveLength(5);
});

it('should show no data indicator', () => {
  loadReports.mockReturnValueOnce([]);
  const node = shallow(<Home {...props} />);

  expect(node.find('.reports NoEntities')).toBePresent();
  expect(node.find('.dashboards NoEntities')).not.toBePresent();
});

it('should contain a link to the edit mode of the entity', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.reports .operations Link').prop('to')).toBe('/report/reportID/edit');
  expect(node.find('.dashboards .operations Link').prop('to')).toBe('/dashboard/dashboardID/edit');
});

it('should display error messages', () => {
  const node = shallow(<Home {...props} error="Something went wrong" />);

  expect(node.find('Message')).toBePresent();
});

it('should contain a link to view all entities', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.reports > Link').prop('to')).toBe('/reports');
  expect(node.find('.dashboards > Link').prop('to')).toBe('/dashboards');
});

it('should display combined tag for combined reports', () => {
  loadReports.mockReturnValue([combinedProcessReport]);

  const node = shallow(<Home {...props} />);

  expect(node.find('.reports .dataTitle')).toIncludeText('Combined');
});

it('should display decision tag for decision reports', () => {
  loadReports.mockReturnValue([decisionReport]);

  const node = shallow(<Home {...props} />);

  expect(node.find('.reports .dataTitle')).toIncludeText('Decision');
});
