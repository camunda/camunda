import React from 'react';

import DurationFilter from './DurationFilter';
import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  const Select = props => <select {...props}>{props.children}</select>;
  Select.Option = props => <option {...props}>{props.children}</option>;

  return {
    Modal,
    Button: props => <button {...props}>{props.children}</button>,
    Input: props => <input {...props} />,
    Select
  };
});

it('should contain a modal', () => {
  const node = mount(<DurationFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should contain a button to abort the filter creation', () => {
  const spy = jest.fn();
  const node = mount(<DurationFilter close={spy} />);

  const abortButton = node.find('#modal_actions button').at(0);

  abortButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const node = mount(<DurationFilter addFilter={spy} />);
  const addButton = node.find('#modal_actions button').at(1);

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
