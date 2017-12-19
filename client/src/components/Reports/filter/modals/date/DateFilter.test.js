import React from 'react';

import DateFilter from './DateFilter';
import {mount} from 'enzyme';

console.error = jest.fn();

jest.mock('./DateFields', () => props => `DateFields: props: ${Object.keys(props)}`);
jest.mock('./DateButton', () => props => `DateButton: props: ${Object.keys(props)}`);


jest.mock('components', () =>{
  const Modal = props => <div id='modal'>{props.children}</div>;
  Modal.Header = props => <div id='modal_header'>{props.children}</div>;
  Modal.Content = props => <div id='modal_content'>{props.children}</div>;
  Modal.Actions = props => <div id='modal_actions'>{props.children}</div>;

  return {
  Modal,
  Button: props => <button {...props}>{props.children}</button>,
  ButtonGroup: props => <div {...props}>{props.children}</div>
}});

it('should contain a modal', () => {
  const node = mount(<DateFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should contain date fields', () => {
  const node = mount(<DateFilter />);

  expect(node).toIncludeText('DateFields');
});

it('should pass the onDateChangeFunction to the DateFields', () => {
  const node = mount(<DateFilter />);

  expect(node).toIncludeText('onDateChange');
});

it('should contain date buttons', () => {
  const node = mount(<DateFilter />);

  expect(node).toIncludeText('DateButton');
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = mount(<DateFilter close={spy}/>);

  const abortButton = node.find('button').at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = mount(<DateFilter addFilter={spy}/>);
  node.setState({
    validDate: true
  });
  const addButton = node.find('button').at(1);

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
