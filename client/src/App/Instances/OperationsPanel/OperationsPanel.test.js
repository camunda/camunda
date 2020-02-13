/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mount} from 'enzyme';
import React from 'react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import OperationsPanel from './OperationsPanel';

import {
  mockOperationFinished,
  mockOperationRunning
} from './OperationsPanel.setup';

import useBatchOperations from './useBatchOperations';
jest.mock('./useBatchOperations');

const mountOperationsPanel = () => {
  return mount(
    <CollapsablePanelProvider>
      <OperationsPanel />
    </CollapsablePanelProvider>
  );
};

describe('OperationsPanel', () => {
  beforeEach(() => {
    useBatchOperations.mockReturnValue({
      batchOperations: [],
      requestBatchOperations: jest.fn()
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should display empty panel on mount', () => {
    // when
    const node = mountOperationsPanel();

    // then
    const entry = node.find('[data-test="operations-entry"]');
    expect(entry).toHaveLength(0);
  });

  it('should render operation entries', () => {
    // given
    useBatchOperations.mockReturnValue({
      batchOperations: [mockOperationRunning, mockOperationFinished],
      requestBatchOperations: jest.fn()
    });

    // when
    const node = mountOperationsPanel();

    // then
    const entry = node.find('[data-test="operations-entry"]');

    const firstEntry = entry.at(0).html();
    const secondEntry = entry.at(1).html();

    expect(entry).toHaveLength(2);
    expect(firstEntry).toContain(mockOperationRunning.id);
    expect(firstEntry).toContain('Retry');
    expect(secondEntry).toContain(mockOperationFinished.id);
    expect(secondEntry).toContain('Cancel');
  });

  it('should request batch operations on mount', () => {
    // given
    useBatchOperations.mockReturnValue({
      batchOperations: [],
      requestBatchOperations: jest.fn()
    });

    // when
    mountOperationsPanel();

    // then
    expect(useBatchOperations().requestBatchOperations).toHaveBeenCalledTimes(
      1
    );
  });

  it('should expand', () => {
    // given
    const node = mountOperationsPanel();

    // when
    node
      .find('[data-test="expand-button"]')
      .first()
      .simulate('click');

    // then
    const expandedPanel = node.find('[data-test="expanded-panel"]');
    const collapsedPanel = node.find('[data-test="collapsed-panel"]');

    expect(collapsedPanel).toHaveStyleRule('visibility', 'hidden');
    expect(expandedPanel).toHaveStyleRule('visibility', 'visible');
  });

  it('should collapse', () => {
    // given
    const node = mountOperationsPanel();

    // when
    node
      .find('[data-test="expand-button"]')
      .first()
      .simulate('click');

    node
      .find('[data-test="collapse-button"]')
      .first()
      .simulate('click');

    // then
    const expandedPanel = node.find('[data-test="expanded-panel"]');
    const collapsedPanel = node.find('[data-test="collapsed-panel"]');

    expect(collapsedPanel).toHaveStyleRule('visibility', 'visible');
    expect(expandedPanel).toHaveStyleRule('visibility', 'hidden');
  });
});
