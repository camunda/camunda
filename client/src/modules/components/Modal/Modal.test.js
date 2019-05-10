/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {ReactComponent as CloseLarge} from 'modules/components/Icon/close-large.svg';

import Modal from './Modal';

const HeaderContent = () => <div>Header Content</div>;
const BodyContent = () => <div>Body Content</div>;
const onModalClose = jest.fn();
const primaryButtonClickHandler = jest.fn();
const isVisible = true;

const mountNode = (props = {}) => {
  return mount(
    <ThemeProvider value="dark">
      <Modal
        {...props}
        onModalClose={onModalClose}
        isVisible={isVisible}
        className="modal-root"
      >
        <Modal.Header>
          <HeaderContent />
        </Modal.Header>
        <Modal.Body>
          <BodyContent />
        </Modal.Body>
        <Modal.Footer>
          <Modal.PrimaryButton onClick={primaryButtonClickHandler}>
            Some Primary Button
          </Modal.PrimaryButton>
        </Modal.Footer>
      </Modal>
    </ThemeProvider>
  );
};

describe('Modal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render modal in a div in the document body', () => {
    // given
    const node = mountNode();

    // then
    expect(document.querySelector('.modal-root')).toBeDefined();

    // Header
    expect(node.find(Modal.Header).find(HeaderContent)).toHaveLength(1);
    expect(
      node
        .find(Modal.Header)
        .find("[data-test='cross-button']")
        .find(CloseLarge)
    ).toHaveLength(1);

    // Body
    expect(node.find(Modal.Body).find(BodyContent)).toHaveLength(1);

    // Footer
    expect(node.find(Modal.Footer).find(Modal.PrimaryButton)).toHaveLength(1);
  });

  it('should render a cross close button in the header', () => {
    // given
    const node = mountNode();

    // when
    node
      .find(Modal.Header)
      .find("button[data-test='cross-button']")
      .prop('onClick')();

    // then
    expect(onModalClose).toBeCalled();
  });

  it('should respond to key presses', () => {
    // given
    mountNode();
    const espcapeKeKeyPressEvent = new KeyboardEvent('keydown', {keyCode: 27});
    const enterkeKeyPressEvent = new KeyboardEvent('keydown', {keyCode: 13});

    // Pressing <ESC>
    // when
    document.dispatchEvent(espcapeKeKeyPressEvent);
    // then
    expect(onModalClose).toBeCalled();

    // Pressing <Enter>
    // when
    document.dispatchEvent(enterkeKeyPressEvent);
    // then
    expect(primaryButtonClickHandler).toBeCalled();
  });
});
