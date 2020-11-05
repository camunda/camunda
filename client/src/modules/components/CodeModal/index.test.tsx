/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {testData} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import Modal from 'modules/components/Modal';
import CodeModal from './index';
import * as Styled from './styled';

const elementMock = {
  addEventListener: jest.fn((event, cb) => {
    //
  }),
};

// @ts-expect-error ts-migrate(2345) FIXME: Type '{ addEventListener: Mock<void, [event: any, ... Remove this comment to see the full error message
jest.spyOn(document, 'getElementById').mockImplementation((id) => {
  return elementMock;
});

function mountNode(props = {}) {
  return mount(
    <ThemeProvider>
      {/* @ts-expect-error ts-migrate(2741) FIXME: Property 'mode' is missing in type '{}' but requir... Remove this comment to see the full error message */}
      <CodeModal {...props} />
    </ThemeProvider>
  );
}

describe('CodeModal', () => {
  it('should not render any modal UI initially', () => {
    const node = mountNode(testData.pageMounts);

    expect(node.find(Modal.Header)).not.toExist();
    expect(node.find(Styled.ModalBody)).not.toExist();
    expect(node.find(Modal.Footer)).not.toExist();
  });

  it('should have a fallback, when incorrect mode property is passed', () => {
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();

    const node = mountNode(testData.userOpensModalWithUnknownMode);

    expect(node.find(Modal.Header)).toExist();
    expect(node.find(Styled.ModalBody)).toExist();
    expect(node.find(Modal.Footer)).toExist();

    global.console.error = originalConsoleError;
  });

  describe('view', () => {
    it('should render not editable code editor', () => {
      const node = mountNode(testData.userOpensViewModal);

      expect(node.find('code').props().contentEditable).toBe(false);
    });

    it('should render close button', () => {
      const node = mountNode(testData.userOpensViewModal);
      const closebtn = node.find('button[data-testid="primary-close-btn"]');

      expect(closebtn).toExist();

      closebtn.simulate('click');
      expect(testData.userOpensViewModal.handleModalClose).toHaveBeenCalled();
    });
  });
});
