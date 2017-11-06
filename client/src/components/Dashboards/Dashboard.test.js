import React from 'react';
import {mount} from 'enzyme';

import Dashboard from './Dashboard';
import {loadDashboard, remove, update} from './service';

jest.mock('./service', () => {
  return {
    loadDashboard: jest.fn(),
    remove: jest.fn(),
    update: jest.fn()
  }
});

jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT to {to}</div>
    },
    Link: ({children, to, onClick, id}) => {
      return <a id={id} href={to} onClick={onClick}>{children}</a>
    }
  }
});

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  }
});

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


describe('edit mode', async () => {

  it('should provide a link to view mode', () => {
    props.match.params.viewMode = 'edit';

    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true});

    expect(node).toIncludeText('Save');
    expect(node).toIncludeText('Cancel');
    expect(node).not.toIncludeText('Edit');
  });

  it('should provide name edit input', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    expect(node.find('input#name')).toBePresent();
  });

  it('should invoke update on save click', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    node.find('a#save').simulate('click');

    expect(update).toHaveBeenCalled();
  });

  it('should update name on input change', async () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});
    expect(node).toHaveState('name', input);
  });

  it('should reset name on cancel', () => {
    props.match.params.viewMode = 'edit';
    const node = mount(<Dashboard {...props} />);
    node.setState({loaded: true, name: 'test name', originalName: 'test name'});

    const input = 'asdf';
    node.find(`input[id="name"]`).simulate('change', {target: {value: input}});

    node.find('a#cancel').simulate('click');
    expect(node).toHaveState('name', 'test name');
  });
});
