import React from 'react';
import {mount} from 'enzyme';

import Popover from './Popover';

jest.mock('components', () => {return {
  Button: props => <button onClick={props.onClick} ref={props.reference}>{props.children}</button>
}});

it('should include a button to toggle the popover', () => {
  const node = mount(<Popover title='Foobar' />);

  expect(node.find('button')).toBePresent();
});

it('should render the provided title in the button', () => {
  const node = mount(<Popover title='Foobar' />);

  expect(node).toIncludeText('Foobar');
});

it('should do not display child content initially', () => {
  const node = mount(<Popover title='a'>Child content</Popover>);

  expect(node).not.toIncludeText('Child content');
});

it('should display child content when clicking on the button', () => {
  const node = mount(<Popover title='a'>Child content</Popover>);

  node.find('button').simulate('click');

  expect(node).toIncludeText('Child content');
});

it('should close the popover when clicking the button again', () => {
  const node = mount(<Popover title='a'>Child content</Popover>);

  node.find('button').simulate('click');
  node.find('button').simulate('click');

  expect(node).not.toIncludeText('Child content');
});

it('should not close the popover when clicking inside the popover', () => {
const node = mount(<Popover title='a'><p>Child content</p></Popover>);

  node.find('button').simulate('click');
  node.find('p').simulate('click');

  expect(node).toIncludeText('Child content');
});
