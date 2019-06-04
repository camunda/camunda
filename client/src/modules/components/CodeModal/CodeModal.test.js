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

const elementMock = {addEventListener: jest.fn()};
jest.spyOn(document, 'getElementById').mockImplementation(asd => {
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
  let node;
  beforeEach(() => {
    node = mountNode(testData.userOpensEditModal);
  });
  it('should not render any modal UI initially', () => {
    node = mountNode(testData.pageMounts);

    expect(node.find(Modal.Header)).not.toExist();
    expect(node.find(Modal.Body)).not.toExist();
    expect(node.find(Modal.Footer)).not.toExist();
  });

  it('should have a fallback, when incorrect mode property is passed', () => {
    // silence prop-type warning
    console.error = jest.fn();

    node = mountNode(testData.userOpensModalWithUnknownMode);

    expect(node.find(Modal.Header)).toExist();
    expect(node.find(Modal.Body)).toExist();
    expect(node.find(Modal.Footer)).toExist();
  });

  it('should render multiple lines for a valid JSON value', () => {
    expect(node.find('code').children().length).toBe(5);
  });

  it('should render a single line for a broken JSON object', () => {
    node = mountNode({
      ...testData.userOpensEditModalWithBrokenJSON
    });

    expect(node.find('code').children().length).toBe(1);
  });

  describe('edit', () => {
    let node;

    beforeEach(() => {
      node = mountNode(testData.userOpensEditModal);
    });

    it('should add an input event listener ', () => {
      expect(elementMock.addEventListener).toBeCalledWith(
        'input',
        expect.any(Function)
      );
    });

    it('should render editable code editor', () => {
      expect(node.find('code').props().contentEditable).toBe(true);
    });

    describe('save button', () => {
      // innerText isn't implemented yet in jsDom.
      //https://stackoverflow.com/questions/54495862/why-does-my-unit-test-fail-to-return-the-text-within-a-single-file-component-whe
      //https://github.com/jsdom/jsdom/issues/1245

      it.only('should allow to save value, when valid & modfied', () => {
        //TODO: check if tests can be written with TextContent
      });
      it('should not allow to save "empty" values', () => {
        //
      });

      it('should not allow to save invalid JSON values', () => {});

      it('should not allow to save an unmodified value', () => {
        expect(node.find("button[data-test='save-btn']").prop('disabled')).toBe(
          true
        );
      });
    });
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
