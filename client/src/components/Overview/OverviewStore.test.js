import React from 'react';
import {shallow} from 'enzyme';
import {StoreProvider} from './OverviewStore';
import {load, create} from './service';
import {checkDeleteConflict, toggleEntityCollection} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    getEntitiesCollections: jest.fn().mockReturnValue({}),
    checkDeleteConflict: jest.fn().mockReturnValue([]),
    toggleEntityCollection: jest.fn().mockReturnValue(jest.fn())
  };
});

jest.mock('./service');

const OverviewStore = StoreProvider.WrappedComponent;

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

it('should load data', () => {
  shallow(<OverviewStore {...props} />);

  expect(load).toHaveBeenCalledWith('report');
  expect(load).toHaveBeenCalledWith('dashboard');
  expect(load).toHaveBeenCalledWith('collection');
});

it('should redirect to new report edit page when creating new report', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<OverviewStore {...props} />);
  await node.instance().componentDidMount();

  await node.instance().createProcessReport();

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/report/newReport/edit?new');
});

it('should reload the list after duplication', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<OverviewStore {...props} />);

  await node.instance().duplicateEntity('Report', processReport)({target: {blur: jest.fn()}});

  expect(load).toHaveBeenCalledWith('report');
});

it('should add the entity to the collection that was duplicated from', async () => {
  create.mockReturnValueOnce('newReport');
  const node = shallow(<OverviewStore {...props} />);

  await node.instance().duplicateEntity('report', processReport, collection)({
    target: {blur: jest.fn()}
  });

  expect(toggleEntityCollection()).toHaveBeenCalledWith({id: 'newReport'}, collection, false);
});

it('should check for deletion conflicts for reports and dashboards', async () => {
  checkDeleteConflict.mockClear();
  const node = shallow(<OverviewStore {...props} />);
  await node.instance().componentDidMount();

  await node.instance().showDeleteModalFor({type: 'entityType', entity: processReport})();

  expect(checkDeleteConflict).toHaveBeenCalledWith(processReport.id, 'entityType');
});

it('should reload the reports after deleting a report', async () => {
  const node = shallow(<OverviewStore {...props} />);

  load.mockClear();

  node.setState({
    deleting: {type: 'report', entity: processReport}
  });

  await node.instance().deleteEntity();

  expect(load).toHaveBeenCalledWith('report');
});

it('should redirect to new dashboard edit page', async () => {
  create.mockReturnValueOnce('newDashboard');
  const node = shallow(<OverviewStore {...props} />);
  await node.instance().componentDidMount();
  await node.instance().createDashboard();

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/dashboard/newDashboard/edit?new');
});

it('should duplicate dashboards', () => {
  create.mockClear();

  const node = shallow(<OverviewStore {...props} />);

  node.instance().duplicateEntity('dashboard', dashboard)({target: {blur: jest.fn()}});

  expect(create).toHaveBeenCalledWith('dashboard', {
    ...dashboard,
    name: dashboard.name + ' - Copy'
  });
});
