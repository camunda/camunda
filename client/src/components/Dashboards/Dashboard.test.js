import React from 'react';
import {mount} from 'enzyme';

import Dashboard from './Dashboard';
import {loadDashboard, remove} from './service';

jest.mock('./service', () => {return {
  loadDashboard: jest.fn(),
  remove: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT to {to}</div>},
  Link: ({children, to}) => {return <a href={to}>{children}</a>}
}});
jest.mock('moment', () => () => {return {
  format: () => 'some date'
}});

const props = {
  match: {params: {id: '1'}}
};

const sampleDashboard = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

loadDashboard.mockReturnValue(sampleDashboard);

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it('should display a loading indicator', () => {
  const node = mount(<Dashboard {...props} />);

  expect(node).toIncludeText('loading');
});

it('should initially load data', () => {
  mount(<Dashboard {...props} />);

  expect(loadDashboard).toHaveBeenCalled();
});

it('should display the key properties of a dashboard', () => {
  const node = mount(<Dashboard {...props} />);

  node.setState({
    loaded: true,
    ...sampleDashboard
  });

  expect(node).toIncludeText(sampleDashboard.name);
  expect(node).toIncludeText(sampleDashboard.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('Edit');
});

it('should provide a link to view mode in edit mode', () => {
  props.match.params.viewMode = 'edit';

  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('Save');
  expect(node).toIncludeText('Cancel');
  expect(node).not.toIncludeText('Edit');
});

it('should remove a dashboard when delete button is clicked', () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  node.find('button').simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the dashboard list on dashboard deletion', async () => {
  const node = mount(<Dashboard {...props} />);
  node.setState({loaded: true});

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /dashboards');
});
