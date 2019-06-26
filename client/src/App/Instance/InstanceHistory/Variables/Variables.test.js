/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createVariables} from 'modules/testUtils';
import {STATE} from 'modules/constants';

import {EMPTY_PLACEHOLDER, NULL_PLACEHOLDER} from './constants';
import Variables from './Variables';

const MODE = {
  EDIT: 'edit',
  ADD: 'add'
};

const mockProps = {
  variables: createVariables(),
  editMode: '',
  instanceState: STATE.ACTIVE,
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

/* Helper function as node.setProps() changes only props of the rootNode, here: <ThemeProvider>*/
const setProps = (node, WrappedComponent, updatedProps) => {
  return node.setProps({
    children: <WrappedComponent {...updatedProps} />
  });
};

describe('Variables', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should render variables table', () => {
    // given
    const node = mountNode();

    // then
    expect(node.find('tr')).toHaveLength(mockProps.variables.length + 1);
    mockProps.variables.forEach(variable => {
      const row = node.find(`tr[data-test="${variable.name}"]`);
      expect(row).toHaveLength(1);
      const columns = row.find('td');
      expect(columns).toHaveLength(3);
      expect(columns.at(0).text()).toContain(variable.name);
      expect(columns.at(1).text()).toContain(variable.value);
    });
  });

  describe('Messages', () => {
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
  });

  describe('Disable "Add Variable" button', () => {
    it('should disable when no editable variable', () => {
      // when
      const node = mountNode({isEditable: false});

      // then
      const addButton = node.find("button[data-test='enter-add-btn']");
      expect(addButton.prop('disabled')).toBe(true);
    });

    it('should disable when editing variable', () => {
      // given
      const node = mountNode({isEditable: true, editMode: MODE.EDIT});

      // then
      const addButton = node.find("button[data-test='enter-add-btn']");
      expect(addButton.prop('disabled')).toBe(true);
    });

    it('should disable when adding variable', () => {
      // given
      const node = mountNode({isEditable: true, editMode: MODE.ADD});

      // then
      const updatedAddButton = node.find("button[data-test='enter-add-btn']");
      expect(updatedAddButton.prop('disabled')).toBe(true);
    });
  });

  describe('Add variable', () => {
    it('should show add variable inputs', () => {
      const node = mountNode({editMode: MODE.ADD});

      // then
      expect(node.find('input[data-test="add-key"]')).toHaveLength(1);
      expect(node.find('textarea[data-test="add-value"]')).toHaveLength(1);
    });

    it('should expose that a variable is being added by the user', () => {
      // given
      const node = mountNode({variables: null});

      const newVariable = 'newVariable';
      const newValue = '1234';

      const addButton = node.find("button[data-test='enter-add-btn']");

      // when
      addButton.simulate('click');
      expect(mockProps.setEditMode.mock.calls[0][0]).toBe(MODE.ADD);

      setProps(node, Variables, {
        ...mockProps,
        editMode: MODE.ADD
      });
      node.update();

      node
        .find("input[data-test='add-key']")
        .simulate('change', {target: {value: newVariable}});
      node
        .find("textarea[data-test='add-value']")
        .simulate('change', {target: {value: newValue}});

      // then
      node.find("button[data-test='save-var-inline-btn']").simulate('click');
      expect(mockProps.setEditMode.mock.calls[1][0]).toBe('');
    });

    it('should expose the new key-value pair', () => {
      const node = mountNode();

      const newVariable = 'newVariable';
      const newValue = '1234';

      setProps(node, Variables, {
        ...mockProps,
        editMode: MODE.ADD
      });

      node
        .find("input[data-test='add-key']")
        .simulate('change', {target: {value: newVariable}});
      node
        .find("textarea[data-test='add-value']")
        .simulate('change', {target: {value: newValue}});

      node.find("button[data-test='save-var-inline-btn']").simulate('click');

      expect(mockProps.onVariableUpdate).toHaveBeenCalledWith(
        newVariable,
        newValue
      );
    });

    describe('disable save button', () => {
      let node;

      beforeEach(() => {
        node = mountNode({editMode: MODE.ADD});
      });

      it('should not allow to save empty values', () => {
        // then
        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });

      it('should not allow to save invalid values', () => {
        const variable = 'variableName';
        // key must be a string to be valid JSON;
        const invalidJSONObject = "{invalidKey: 'value'}";

        // when
        node
          .find("input[data-test='add-key']")
          .simulate('change', {target: {value: variable}});
        node
          .find("textarea[data-test='add-value']")
          .simulate('change', {target: {value: invalidJSONObject}});

        // then
        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });

      it('should not allow to save variables which key already exists', () => {
        const alreadyExistingVariable = 'clientNo';
        const newValue = '1234';

        // when
        node
          .find("input[data-test='add-key']")
          .simulate('change', {target: {value: alreadyExistingVariable}});
        node
          .find("textarea[data-test='add-value']")
          .simulate('change', {target: {value: newValue}});

        // then
        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });
    });
  });

  describe('Edit variable', () => {
    let node;

    beforeEach(() => {
      node = mountNode();
    });

    it('should show edit in-line buttons for running instances', () => {
      let openInlineEditButtons = node.find(
        "button[data-test='enter-edit-btn']"
      );
      expect(openInlineEditButtons).toHaveLength(3);

      node = mountNode({instanceState: STATE.INCIDENT});

      openInlineEditButtons = node.find("button[data-test='enter-edit-btn']");
      expect(openInlineEditButtons).toHaveLength(3);
    });

    it('should NOT show edit in-line buttons for finished instances', () => {
      node = mountNode({instanceState: STATE.CANCELLED});

      let openInlineEditButtons = node.find(
        "button[data-test='enter-edit-btn']"
      );
      expect(openInlineEditButtons).toHaveLength(0);

      node = mountNode({instanceState: STATE.COMPLETED});

      openInlineEditButtons = node.find("button[data-test='enter-edit-btn']");
      expect(openInlineEditButtons).toHaveLength(0);
    });

    it('should show open-modal button, when edit JSON data', () => {
      // given
      const openInlineEditButtons = node.find(
        "button[data-test='enter-edit-btn']"
      );

      // when
      // variable with JSON data:
      openInlineEditButtons.at(1).simulate('click');

      setProps(node, Variables, {
        ...mockProps,
        editMode: MODE.EDIT
      });

      // then
      expect(node.find("textarea[data-test='edit-value']")).toExist();
      expect(node.find("button[data-test='open-modal-btn']")).toExist();
    });

    it('should show inline edit functionality', () => {
      // given
      const openInlineEditButtons = node.find(
        "button[data-test='enter-edit-btn']"
      );
      // when
      openInlineEditButtons.first().simulate('click');

      setProps(node, Variables, {
        ...mockProps,
        editMode: MODE.EDIT
      });

      // then
      expect(node.find("textarea[data-test='edit-value']")).toExist();

      expect(node.find("button[data-test='exit-edit-inline-btn']")).toExist();
      expect(node.find("button[data-test='save-var-inline-btn']")).toExist();
    });

    it('should expose that a variable is being edited by the user', () => {
      // given
      const openInlineEditButtons = node.find(
        "button[data-test='enter-edit-btn']"
      );
      const newJsonValue = '{"key": "MyValue"}';

      // when
      // edit mode is true
      openInlineEditButtons.first().simulate('click');
      expect(mockProps.setEditMode).toHaveBeenNthCalledWith(1, MODE.EDIT);

      setProps(node, Variables, {
        ...mockProps,
        editMode: MODE.EDIT
      });
      node.update();

      node
        .find("textarea[data-test='edit-value']")
        .simulate('change', {target: {value: newJsonValue}});

      // then
      // edit mode is false
      node.find("button[data-test='save-var-inline-btn']").simulate('click');
      expect(mockProps.setEditMode).toHaveBeenNthCalledWith(2, '');
    });

    describe('disable save button', () => {
      beforeEach(() => {
        const openInlineEditButtons = node.find(
          "button[data-test='enter-edit-btn']"
        );
        //given
        openInlineEditButtons.first().simulate('click');
      });

      it('should not allow to save empty values', () => {
        // given
        const emptyValue = '';

        setProps(node, Variables, {
          ...mockProps,
          editMode: MODE.EDIT
        });
        node.update();

        node
          .find("textarea[data-test='edit-value']")
          .simulate('change', {target: {value: emptyValue}});

        // then
        node.find("button[data-test='save-var-inline-btn']").simulate('click');

        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });

      it('should not allow to save invalid values', () => {
        // given
        // key must be a string to be valid JSON;
        const invalidJSONObject = "{invalidKey: 'value'}";

        setProps(node, Variables, {
          ...mockProps,
          editMode: MODE.EDIT
        });
        node.update();

        // when
        node
          .find("textarea[data-test='edit-value']")
          .simulate('change', {target: {value: invalidJSONObject}});

        // then
        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });

      it('should not allow to save an unmodified value', () => {
        const unmodifiedValue = mockProps.variables[0].value;

        setProps(node, Variables, {
          ...mockProps,
          editMode: MODE.EDIT
        });
        node.update();

        // when
        node
          .find("textarea[data-test='edit-value']")
          .simulate('change', {target: {value: unmodifiedValue}});

        // then
        expect(
          node.find("button[data-test='save-var-inline-btn']").prop('disabled')
        ).toBe(true);
      });
    });
  });
});
