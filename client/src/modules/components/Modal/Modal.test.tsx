/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mount} from 'enzyme';

import Modal from './Modal';

it('should not render anything if the modal is not opened', () => {
  const node = mount(<Modal>ModalContent</Modal>);

  expect(node.html()).toBe(null);
});

it('should render basic children', () => {
  const node = mount(<Modal open>ModalContent</Modal>);

  expect(node).toIncludeText('ModalContent');
});

it('should call the onConfirm function when enter is pressed', () => {
  const spy = jest.fn();
  const node = mount(
    <Modal open onConfirm={spy}>
      <input />
    </Modal>
  );

  node.find('.Modal').simulate('keydown', {key: 'Enter'});

  expect(spy).toHaveBeenCalled();
});

it('should not call the onConfirm function when enter is pressed on radio/checkbox element', () => {
  const spy = jest.fn();
  const node = mount(
    <Modal open onConfirm={spy}>
      <input />
    </Modal>
  );

  node
    .find('.Modal')
    .simulate('keydown', {key: 'Enter', target: {tagName: 'input', type: 'radio'}});

  expect(spy).not.toHaveBeenCalled();
});

it('should not call the onConfirm function when enter is pressed but the modal is not open', () => {
  const spy = jest.fn();
  mount(<Modal onConfirm={spy} />);

  const event = new KeyboardEvent('keydown', {key: 'Enter', bubbles: true});
  document.body.dispatchEvent(event);

  expect(spy).not.toHaveBeenCalled();
});

it('should not call the onConfirm function when enter is pressed but the focus was on a button', () => {
  const spy = jest.fn();
  mount(
    <Modal open onConfirm={spy}>
      <button>Action Button</button>
    </Modal>
  );

  const event = new KeyboardEvent('keydown', {key: 'Enter', bubbles: true});
  document.querySelector('button')?.dispatchEvent(event);

  expect(spy).not.toHaveBeenCalled();
});

it('should call the onClose function when escape is pressed', () => {
  const spy = jest.fn();
  const node = mount(
    <Modal open onClose={spy}>
      <button>Action Button</button>
    </Modal>
  );

  node.find('.Modal').simulate('keydown', {key: 'Escape'});

  expect(spy).toHaveBeenCalled();
});

it('should automatically focus focusable element in modal', () => {
  mount(
    <Modal open>
      <button className="focusBtn" />
    </Modal>
  );

  expect(document.activeElement?.getAttribute('class')).toBe('focusBtn');
});

it('should not automatically focus focusable element in modal if noAutoFocus prop is present', () => {
  mount(
    <Modal open noAutoFocus>
      <button className="focusBtn" />
    </Modal>
  );

  expect(document.activeElement?.getAttribute('class')).toBe('Modal__content-container');
});

it('should focus the modal container if all focusable elements are disabled', () => {
  mount(
    <Modal open>
      <button className="focusBtn" disabled />
    </Modal>
  );

  expect(document.activeElement?.getAttribute('class')).toBe('Modal__content-container');
});

it('should trap focus', () => {
  const node = mount(<Modal open />);

  node.find('.Modal__scroll-container div').last().simulate('focus');

  expect(document.activeElement?.getAttribute('class')).toBe('Modal__content-container');
});

it('should clean up on unmount', () => {
  const spy = jest.fn();
  document.body.removeChild = spy;

  const node = mount(<Modal />);

  node.unmount();

  expect(spy).toHaveBeenCalled();
});

describe('Header', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });

  it('should contain a close button', () => {
    const node = mount(
      <Modal open>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    expect(node.find('button').length).toBe(1);
  });

  it('should call the onClose function on close button click', () => {
    const spy = jest.fn();
    const node = mount(
      <Modal open onClose={spy}>
        <Modal.Header>
          <div className="test">test</div>
        </Modal.Header>
      </Modal>
    );
    node.find('button').simulate('click');
    expect(spy).toHaveBeenCalled();
  });
});

describe('Content', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open>
        <Modal.Content>
          <div className="test">test</div>
        </Modal.Content>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });
});

describe('Actions', () => {
  it('should render children', () => {
    const node = mount(
      <Modal open>
        <Modal.Actions>
          <div className="test">test</div>
        </Modal.Actions>
      </Modal>
    );
    expect(node.find('.test').length).toBe(1);
  });
});
