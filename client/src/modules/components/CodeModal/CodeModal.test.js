/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {testData} from './CodeModal.setup';
import {ThemeProvider} from 'modules/theme';

import Modal from 'modules/components/Modal';
import CodeModal from './CodeModal';

const elementMock = {
  addEventListener: jest.fn((event, cb) => {
    //
  })
};

jest.spyOn(document, 'getElementById').mockImplementation(id => {
  return elementMock;
});

function mountNode(props = {}) {
  return mount(
    <ThemeProvider>
      <CodeModal {...props} />
    </ThemeProvider>
  );
}

describe('CodeModal', () => {
  it('should not render any modal UI initially', () => {
    const node = mountNode(testData.pageMounts);

    expect(node.find(Modal.Header)).not.toExist();
    expect(node.find(Modal.Body)).not.toExist();
    expect(node.find(Modal.Footer)).not.toExist();
  });

  it('should have a fallback, when incorrect mode property is passed', () => {
    // silence prop-type warning
    console.error = jest.fn();

    const node = mountNode(testData.userOpensModalWithUnknownMode);

    expect(node.find(Modal.Header)).toExist();
    expect(node.find(Modal.Body)).toExist();
    expect(node.find(Modal.Footer)).toExist();
  });

  describe('view', () => {
    let node;

    beforeEach(() => {
      node = mountNode(testData.userOpensViewModal);
    });

    it('should render not editable code editor', () => {
      expect(node.find('code').props().contentEditable).toBe(false);
    });

    it('should render close button', () => {
      const closebtn = node.find('button[data-test="primary-close-btn"]');

      expect(closebtn).toExist();

      closebtn.simulate('click');
      expect(testData.userOpensViewModal.handleModalClose).toHaveBeenCalled();
    });
  });
});
