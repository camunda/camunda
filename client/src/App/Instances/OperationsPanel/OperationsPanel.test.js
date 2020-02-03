/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mount} from 'enzyme';
import React from 'react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import OperationsPanel from './OperationsPanel';

import useDataManager from 'modules/hooks/useDataManager';

jest.mock('modules/hooks/useDataManager');

const mountOperationsPanel = () => {
  return mount(
    <DataManagerProvider>
      <CollapsablePanelProvider>
        <OperationsPanel />
      </CollapsablePanelProvider>
    </DataManagerProvider>
  );
};

describe('OperationsPanel', () => {
  beforeEach(() => {
    createMockDataManager();
  });

  it('should display fetched data', () => {
    // given
    const response = [{id: 'MyOperationId', type: 'RESOLVE_INCIDENT'}];
    useDataManager.mockReturnValue({
      subscribe: jest.fn((topic, statehooks, cb) => {
        cb(response);
      })
    });

    // when
    const node = mountOperationsPanel();

    // then
    const entry = node.find('[data-test="operations-entry"]');
    expect(entry.html()).toContain('MyOperationId');
    expect(entry.html()).toContain('Retry');
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
