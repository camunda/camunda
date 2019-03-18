import React from 'react';
import {shallow} from 'enzyme';
import OverviewWithProvider from './Overview';
import {Dropdown, Button} from 'components';

const wrapper = shallow(<OverviewWithProvider />);
const Overview = wrapper.props().children.type.WrappedComponent;

const props = {
  store: {
    loading: false
  },
  createProcessReport: jest.fn()
};

it('should show a loading indicator', () => {
  const node = shallow(<Overview {...props} store={{loading: true}} />);

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should show create Report buttons', async () => {
  const node = shallow(<Overview {...props} />);

  expect(node.find('.createButton')).toBePresent();
});

it('should have a Dropdown with more creation options', async () => {
  const node = shallow(<Overview {...props} />);

  expect(node.find('.createButton').find(Dropdown)).toBePresent();
  expect(node.find('.createButton').find(Dropdown)).toMatchSnapshot();
});

it('should invoke createProcessReport when clicking create button', async () => {
  props.createProcessReport.mockReturnValueOnce('newReport');
  const node = shallow(<Overview {...props} />);

  await node
    .find('.createButton')
    .find(Button)
    .at(1)
    .simulate('click');
});

it('should display error messages', async () => {
  const node = shallow(<Overview {...props} error="Something went wrong" />);

  expect(node.find('Message')).toBePresent();
});
