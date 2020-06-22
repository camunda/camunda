/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mount} from 'enzyme';
import React from 'react';
import ConfirmOperationModal from './index';
import {mockProps} from './index.setup';

describe('ConfirmOperationModal', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('should render', () => {
    // when
    const node = mount(<ConfirmOperationModal {...mockProps} />);

    // then
    expect(node.html()).toContain(mockProps.bodyText);
    expect(node.html()).toContain('Click "Apply" to proceed.');
  });

  it('should call onApplyClick', () => {
    // given
    const node = mount(<ConfirmOperationModal {...mockProps} />);

    // when
    const button = node.find('button[title="Apply"]');
    button.simulate('click');

    // then
    expect(mockProps.onApplyClick).toHaveBeenCalled();
    expect(mockProps.onCancelClick).not.toHaveBeenCalled();
    expect(mockProps.onModalClose).not.toHaveBeenCalled();
  });

  it('should call onCancelClick on cancel', () => {
    // given
    const node = mount(<ConfirmOperationModal {...mockProps} />);

    // when
    const button = node.find('button[title="Cancel"]');
    button.simulate('click');

    // then
    expect(mockProps.onCancelClick).toHaveBeenCalled();
    expect(mockProps.onModalClose).not.toHaveBeenCalled();
    expect(mockProps.onApplyClick).not.toHaveBeenCalled();
  });

  it('should call onModalClose on cross click', () => {
    // given
    const node = mount(<ConfirmOperationModal {...mockProps} />);

    // when
    const button = node.find('[data-test="cross-button"]').first();
    button.simulate('click');

    // then
    expect(mockProps.onModalClose).toHaveBeenCalled();
    expect(mockProps.onCancelClick).not.toHaveBeenCalled();
    expect(mockProps.onApplyClick).not.toHaveBeenCalled();
  });
});
