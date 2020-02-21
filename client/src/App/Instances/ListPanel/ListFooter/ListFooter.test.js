/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import InstanceSelectionContext from 'modules/contexts/InstanceSelectionContext';

import Dropdown from 'modules/components/Dropdown';
import ListFooter from './ListFooter';
import Paginator from './Paginator';
import {Copyright} from './styled';

const defaultContext = {getSelectedCount: () => 0};

const defaultProps = {
  onFirstElementChange: jest.fn(),
  perPage: 10,
  firstElement: 0,
  filterCount: 9,
  dataManager: {},
  hasContent: true
};

describe('ListFooter', () => {
  beforeEach(() => {});

  const renderFooter = ({
    props: additionalProps,
    context: additionalContext
  } = {}) => {
    const context = {...defaultContext, ...additionalContext};
    const props = {...defaultProps, ...additionalProps};

    return mount(
      <InstanceSelectionContext.Provider value={context}>
        <ListFooter {...props} />
      </InstanceSelectionContext.Provider>
    );
  };

  it('should show pagination', () => {
    const node = renderFooter({props: {filterCount: 11}});

    expect(node.find(Dropdown).exists()).toBe(false);
    expect(node.find(Paginator).exists()).toBe(true);
    expect(node.find(Copyright).exists()).toBe(true);
  });

  it('should not show pagination', () => {
    const node = renderFooter({props: {filterCount: 9}});

    expect(node.find(Dropdown).exists()).toBe(false);
    expect(node.find(Paginator).exists()).toBe(false);
    expect(node.find(Copyright).exists()).toBe(true);
  });

  it('should render copyright note', () => {
    const node = renderFooter();
    expect(node.find(Copyright).exists()).toBe(true);
  });

  it('should show Dropdown when there is selection', () => {
    const node = renderFooter({
      context: {getSelectedCount: () => 2}
    });

    const button = node.find(Dropdown);
    expect(button.exists()).toBe(true);
    expect(button.text()).toContain('Apply Operation on 2 Instances...');
  });

  it('should not show Paginator when hasContent is false', () => {
    const node = renderFooter({
      props: {hasContent: false, filterCount: 11},
      context: {getSelectedCount: () => 2}
    });

    expect(node.find(Dropdown).exists()).toBe(false);
    expect(node.find(Paginator).exists()).toBe(false);
    expect(node.find(Copyright).exists()).toBe(true);
  });
});
