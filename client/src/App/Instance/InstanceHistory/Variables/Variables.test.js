/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createVariables} from 'modules/testUtils';

import {EMPTY_PLACEHOLDER, NULL_PLACEHOLDER} from './constants';
import Variables from './Variables';

const mockProps = {
  variables: createVariables(),
  isEditMode: false,
  onVariableUpdate: jest.fn(),
  isEditable: true,
  setVariables: jest.fn(),
  setEditMode: jest.fn()
};

function mountNode(props = {}) {
  return mount(
    <ThemeProvider>
      <Variables {...mockProps} {...props} />
    </ThemeProvider>
  );
}

describe('Variables', () => {
  it('should render proper message for non existing variables', () => {
    // given
    const node = mountNode({variables: null});

    // then
    expect(node.contains(NULL_PLACEHOLDER)).toBe(true);
  });

  it('should render proper message for empty variables', () => {
    // given
    const node = mountNode({variables: []});

    // then
    expect(node.contains(EMPTY_PLACEHOLDER)).toBe(true);
  });

  it('should render variables table', () => {
    // given
    const variables = createVariables();
    const node = mountNode({variables});

    // then
    expect(node.find('tr')).toHaveLength(variables.length + 1);
    variables.forEach(variable => {
      const row = node.find(`tr[data-test="${variable.name}"]`);
      expect(row).toHaveLength(1);
      const columns = row.find('td');
      expect(columns).toHaveLength(3);
      expect(columns.at(0).text()).toContain(variable.name);
      expect(columns.at(1).text()).toContain(variable.value);
    });
  });

  describe('add variable', () => {
    it('should disable add button when variables are not editable', () => {
      // given
      const node = mountNode({isEditable: false});

      // then
      expect(
        node.find("button[data-test='add-var-btn']").prop('disabled')
      ).toBe(true);
    });

    it('should show adding variable inputs', () => {
      // given
      const node = mountNode({isEditMode: true});

      // then
      expect(node.find('input[type="text"]')).toHaveLength(1);
      expect(node.find('textarea')).toHaveLength(1);
    });
  });
});
