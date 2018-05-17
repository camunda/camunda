import React from 'react';
import {mount} from 'enzyme';

import ExternalModal from './ExternalModal';

jest.mock('components', () => {
  const Modal = props => <div id="Modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Input: props => <input {...props}>{props.children}</input>,
    Button: props => <button {...props}>{props.children}</button>,
    ControlGroup: props => <div>{props.children}</div>
  };
});

it('should render an input field', () => {
  const node = mount(<ExternalModal />);

  expect(node.find('input.ExternalModal__input')).toBePresent();
});

it('should call the callback when adding an external source', () => {
  const spy = jest.fn();
  const node = mount(<ExternalModal confirm={spy} />);

  node.setState({
    source: 'externalStuff'
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: '',
    configuration: {
      external: 'externalStuff'
    }
  });
});
