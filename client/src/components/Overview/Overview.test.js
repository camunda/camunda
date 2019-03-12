import React from 'react';
import {shallow} from 'enzyme';
import OverviewWithErrorHandling from './Overview';
import {load, create, update} from './service';
import {checkDeleteConflict} from 'services';

import {Button, Dropdown} from 'components';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    checkDeleteConflict: jest.fn().mockReturnValue([])
  };
});

jest.mock('./service');

const Overview = OverviewWithErrorHandling.WrappedComponent;

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
    entities: [processReport]
  }
};

beforeAll(() => {
  load.mockReturnValueOnce([collection]);
  load.mockReturnValueOnce([processReport]);
  load.mockReturnValueOnce([dashboard]);
});

it('should show a loading indicator', () => {
  const node = shallow(<Overview {...props} />);

  node.setState({loading: true});

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should load data', () => {
  shallow(<Overview {...props} />);

  expect(load).toHaveBeenCalledWith('report');
  expect(load).toHaveBeenCalledWith('dashboard');
  expect(load).toHaveBeenCalledWith('collection');
});

it('should show create Report buttons', async () => {
  const node = shallow(<Overview {...props} />);

  await node.instance().componentDidMount();

  expect(node.find('.createButton')).toBePresent();
});

it('should redirect to new report edit page when creating new report', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<Overview {...props} />);
  await node.instance().componentDidMount();

  await node
    .find('.createButton')
    .find(Button)
    .at(1)
    .simulate('click');

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/report/newReport/edit?new');
});

it('should reload the list after duplication', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<Overview {...props} />);

  await node.instance().duplicateEntity('Report', processReport)({target: {blur: jest.fn()}});

  expect(load).toHaveBeenCalledWith('report');
});

it('should add the entity to the collection that was duplicated from', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<Overview {...props} />);

  await node.instance().duplicateEntity('report', processReport, collection)({
    target: {blur: jest.fn()}
  });

  expect(update).toHaveBeenCalledWith('collection', 'aCollectionId', {
    data: {entities: ['reportID', 'newReport']}
  });
});

it('should check for deletion conflicts for reports and dashboards', async () => {
  checkDeleteConflict.mockClear();
  const node = shallow(<Overview {...props} />);
  await node.instance().componentDidMount();

  await node.instance().showDeleteModalFor({type: 'entityType', entity: processReport})();

  expect(checkDeleteConflict).toHaveBeenCalledWith(processReport.id, 'entityType');
});

it('should have a Dropdown with more creation options', async () => {
  const node = shallow(<Overview {...props} />);
  await node.instance().componentDidMount();

  expect(node.find('.createButton').find(Dropdown)).toBePresent();
  expect(node.find('.createButton').find(Dropdown)).toMatchSnapshot();
});

it('should reload the reports after deleting a report', async () => {
  const node = shallow(<Overview {...props} />);

  load.mockClear();

  node.setState({
    deleting: {type: 'report', entity: processReport}
  });

  await node.instance().deleteEntity();

  expect(load).toHaveBeenCalledWith('report');
});

it('should display error messages', async () => {
  const node = shallow(<Overview {...props} error="Something went wrong" />);
  await node.instance().componentDidMount();

  expect(node.find('Message')).toBePresent();
});

it('should redirect to new dashboard edit page', async () => {
  create.mockReturnValueOnce('newDashboard');
  const node = shallow(<Overview {...props} />);
  await node.instance().componentDidMount();

  await node
    .find('.createButton')
    .find(Button)
    .first()
    .simulate('click');

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/dashboard/newDashboard/edit?new');
});

it('should duplicate dashboards', () => {
  create.mockClear();

  const node = shallow(<Overview {...props} />);

  node.instance().duplicateEntity('dashboard', dashboard)({target: {blur: jest.fn()}});

  expect(create).toHaveBeenCalledWith('dashboard', {
    ...dashboard,
    name: dashboard.name + ' - Copy'
  });
});

it('should correctly add report to a collection', async () => {
  const node = shallow(<Overview {...props} />);
  await node.instance().toggleEntityCollection(processReport, collection, false)();
  expect(update).toHaveBeenCalledWith('collection', 'aCollectionId', {
    data: {entities: ['reportID', 'reportID']}
  });
});

it('should correctly remove report to a collection', async () => {
  const node = shallow(<Overview {...props} />);
  await node.instance().toggleEntityCollection(processReport, collection, true)();
  expect(update).toHaveBeenCalledWith('collection', 'aCollectionId', {data: {entities: []}});
});
