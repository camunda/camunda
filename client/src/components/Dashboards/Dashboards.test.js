import React from 'react';
import {mount} from 'enzyme';

import Dashboards from './Dashboards';

import {loadDashboards, create} from './service';

const sampleDashboard = {
  id: '1',
  name: 'Test Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
};

jest.mock('./service', () => {return {
  loadDashboards: jest.fn(),
  remove: jest.fn(),
  create: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT to {to}</div>},
}});
jest.mock('moment', () => () => {return {
  format: () => 'some date'
}});
jest.mock('../Table', () => {return {
  Table: ({data}) => <table><tbody><tr><td>{JSON.stringify(data)}</td></tr></tbody></table>
}});

loadDashboards.mockReturnValue([sampleDashboard]);

it('should display a loading indicator', () => {
  const node = mount(<Dashboards />);

  expect(node).toIncludeText('loading');
});

it('should initially load data', () => {
  mount(<Dashboards />);

  expect(loadDashboards).toHaveBeenCalled();
});

it('should display a table with the results', () => {
  const node = mount(<Dashboards />);

  node.setState({
    loaded: true,
    data: [sampleDashboard]
  });

  expect(node).toIncludeText(sampleDashboard.name);
  expect(node).toIncludeText(sampleDashboard.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should call new dashboard on click on the new dashboard button and redirect to the new dashboard', async () => {
  create.mockReturnValueOnce('2');
  const node = mount(<Dashboards />);

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /dashboard/2/edit');
});
