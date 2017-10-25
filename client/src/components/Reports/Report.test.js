import React from 'react';
import {mount} from 'enzyme';

import Report from './Report';
import {loadSingleReport, remove} from './service';

jest.mock('./service', () => {return {
  loadSingleReport: jest.fn(),
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

const sampleReport = {
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200'
};

loadSingleReport.mockReturnValue(sampleReport);

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it('should display a loading indicator', () => {
  const node = mount(<Report {...props} />);

  expect(node).toIncludeText('loading');
});

it('should initially load data', () => {
  mount(<Report {...props} />);

  expect(loadSingleReport).toHaveBeenCalled();
});

it('should display the key properties of a report', () => {
  const node = mount(<Report {...props} />);

  node.setState({
    loaded: true,
    ...sampleReport
  });

  expect(node).toIncludeText(sampleReport.name);
  expect(node).toIncludeText(sampleReport.lastModifier);
  expect(node).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('Edit');
});

it('should provide a link to view mode in edit mode', () => {
  props.match.params.viewMode = 'edit';

  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  expect(node).toIncludeText('Save');
  expect(node).toIncludeText('Cancel');
  expect(node).not.toIncludeText('Edit');
});

it('should remove a report when delete button is clicked', () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  node.find('button').simulate('click');

  expect(remove).toHaveBeenCalledWith('1');
});

it('should redirect to the report list on report deletion', async () => {
  const node = mount(<Report {...props} />);
  node.setState({loaded: true});

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT to /reports');
});
