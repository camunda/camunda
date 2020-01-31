/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mount} from 'enzyme';
import React from 'react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import OperationsPanel from './OperationsPanel';

describe('OperationsPanel', () => {
  it('should expand', () => {
    // given
    const node = mount(
      <CollapsablePanelProvider>
        <OperationsPanel />
      </CollapsablePanelProvider>
    );

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
    const node = mount(
      <CollapsablePanelProvider>
        <OperationsPanel />
      </CollapsablePanelProvider>
    );

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
