/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {act} from 'react-dom/test-utils';

import Dropdown from 'modules/components/Dropdown';
import {OPERATION_TYPE} from 'modules/constants';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {
  mockUseOperationApply,
  mockConfirmOperationModal
} from './CreateOperationDropdown.setup';

import CreateOperationDropdown from './';

jest.mock('../ConfirmOperationModal', () => mockConfirmOperationModal);
jest.mock('./useOperationApply', () => () => mockUseOperationApply);

describe('CreateOperationDropdown', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('should render button', () => {
    // given
    const node = mount(
      <CollapsablePanelProvider>
        <CreateOperationDropdown label="MyLabel" selectedCount={2} />
      </CollapsablePanelProvider>
    );

    // when
    const button = node.find('button');

    // then
    expect(button.text()).toContain('MyLabel');
  });

  it('should show dropdown menu on click', () => {
    // given
    const node = mount(
      <CollapsablePanelProvider>
        <CreateOperationDropdown label="MyLabel" selectedCount={2} />
      </CollapsablePanelProvider>
    );
    const button = node.find('[data-test="dropdown-toggle"]').first();

    // when
    button.simulate('click');

    // then
    const retryButton = node
      .find(Dropdown.Option)
      .at(0)
      .find('button');

    const cancelButton = node
      .find(Dropdown.Option)
      .at(1)
      .find('button');

    expect(retryButton.text()).toContain('Retry');
    expect(cancelButton.text()).toContain('Cancel');
  });

  it('should show modal on retry click', () => {
    // given
    const node = mount(
      <CollapsablePanelProvider>
        <CreateOperationDropdown label="MyLabel" selectedCount={2} />
      </CollapsablePanelProvider>
    );
    const button = node.find('[data-test="dropdown-toggle"]').first();

    // when
    button.simulate('click');

    const retryButton = node
      .find(Dropdown.Option)
      .at(0)
      .find('button');

    retryButton.simulate('click');

    node.update();

    // then
    const mockModal = node.find('[data-test="mock-confirm-operation-modal"]');

    expect(mockModal).toExist();
    expect(mockModal.text()).toBe('About to retry 2 Instances.');
  });

  it('should show modal on cancel click', () => {
    // given
    const node = mount(
      <CollapsablePanelProvider>
        <CreateOperationDropdown label="MyLabel" selectedCount={2} />
      </CollapsablePanelProvider>
    );

    const button = node.find('[data-test="dropdown-toggle"]').first();

    // when
    button.simulate('click');

    const cancelButton = node
      .find(Dropdown.Option)
      .at(1)
      .find('button');

    cancelButton.simulate('click');

    node.update();

    // then
    const mockModal = node.find('[data-test="mock-confirm-operation-modal"]');

    expect(mockModal).toExist();
    expect(mockModal.text()).toBe('About to cancel 2 Instances.');
  });

  it('should call applyBatchOperation', () => {
    const node = mount(
      <CollapsablePanelProvider>
        <CreateOperationDropdown label="MyLabel" selectedCount={2} />
      </CollapsablePanelProvider>
    );

    const button = node.find('[data-test="dropdown-toggle"]').first();

    // when
    button.simulate('click');

    // then
    const retryButton = node
      .find(Dropdown.Option)
      .at(1)
      .find('button');

    retryButton.simulate('click');

    const {onApplyClick} = node.find(mockConfirmOperationModal).props();

    act(() => {
      onApplyClick();
    });

    expect(mockUseOperationApply.applyBatchOperation).toHaveBeenCalledTimes(1);
    expect(mockUseOperationApply.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE
    );
  });
});
