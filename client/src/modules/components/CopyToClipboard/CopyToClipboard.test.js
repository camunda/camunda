import React from 'react';
import { mount } from 'enzyme';

import CopyToClipboard from './CopyToClipboard';


jest.mock('components', () => {return {
  Button: (props) => <button {...props}></button>,
  Input: (props) => <input ref={props.reference} value={props.value} readOnly='readonly'/>
}});


it('should render without crashing', () => {
  mount(<CopyToClipboard />);
});

it('should set a value to its Input as provided as a prop', () => {
  const val = '123';
  const node = mount(<CopyToClipboard value={val} />);
  
  expect(node.find('input').at(0)).toHaveValue(val);
})

it('should copy the value of the input field to the clipboard on clicking the "Copy" button', () => {
  const node = mount(<CopyToClipboard />);
  
  node.find('button').simulate('click');
  expect(document.execCommand).toHaveBeenCalledWith('Copy');
})

