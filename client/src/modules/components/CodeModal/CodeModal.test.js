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

  it('should render multiple lines for a valid JSON value', () => {
    const node = mountNode(testData.userOpensEditModal);

    expect(node.find('code').children().length).toBe(5);
  });

  it('should render a single line for a broken JSON object', () => {
    const node = mountNode({
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

    describe.skip('save button', () => {
      //check if tests can be written with TextContent
      // get access to the whole code editor text
      // change the code editor text
      // check if the button state changed accordingly
      it('should allow to save value, when valid & modfied', () => {
        // node.find('code').simulate('input', {
        //   target: {
        //     textContent: '{"firstname":"asdasdMax"}'
        //   }
        // });

        // const domNode = node.find('code').getDOMNode();
        // domNode.textContent =
        //   '{"firstname":"asdasdMax","lastname":"Muster","age":31}';

        node.update();

        // expect(elementMock.addEventListener).toBeCalledWith(
        //   'input',
        //   expect.any(Function).toBeCalledWith(
        //     expect.objectContaining({
        //       target: {
        //         textContent: '{"firstname":"asdasdMax"}'
        //       }
        //     })
        //   )
        // );

        // expect(node.find('code').text()).toEqual('{"firstname":"asdasdMax"}');

        // expect(node.find("button[data-test='save-btn']").prop('disabled')).toBe(
        //   false
        // );
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
