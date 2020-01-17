/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ListFooter from './ListFooter';
import Paginator from './Paginator';
import {Copyright} from './styled';

jest.mock('modules/utils/bpmn');

describe('ListFooter', () => {
  const renderFooter = props => {
    return shallow(
      <ListFooter
        onFirstElementChange={jest.fn()}
        onAddNewSelection={jest.fn()}
        onAddToSelectionById={jest.fn()}
        onAddToOpenSelection={jest.fn()}
        perPage={10}
        firstElement={0}
        filterCount={9}
        selection={{ids: [], excludeIds: []}}
        selections={[{selectionId: 0}, {selectionId: 1}]}
        dataManager={{}}
        hasContent={true}
        {...props}
      />
    );
  };

  it('should pagination only if required', () => {
    const node = renderFooter();
    expect(node.find(Paginator).exists()).toBe(false);
    node.setProps({filterCount: 11});
    expect(node.find(Paginator).exists()).toBe(true);
    expect(node.find(Copyright).exists()).toBe(true);
  });

  it('should render button if no selection exists', () => {
    const node = renderFooter();
    node.setProps({selections: []});
    expect(node.find(Copyright).exists()).toBe(true);
  });

  it('should show copyright note only when hasContent is false', () => {
    const node = renderFooter({hasContent: false});

    expect(node.find(Paginator).exists()).toBe(false);
    expect(node.find(Copyright).exists()).toBe(true);
  });
});
