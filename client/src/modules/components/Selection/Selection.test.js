/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount, shallow} from 'enzyme';

import {
  createInstance,
  flushPromises,
  createOperation
} from 'modules/testUtils';
import {OPERATION_STATE} from 'modules/constants';
import {ThemeProvider} from 'modules/theme';
import StateIcon from 'modules/components/StateIcon';
import Dropdown from 'modules/components/Dropdown';
import Selection from './Selection';
import * as Styled from './styled';

const mockInstance = createInstance({
  operations: [createOperation({state: 'SENT'})]
});
const mockMap = new Map([['1', mockInstance]]);

const mockOnClick = jest.fn();
const mockOnRetry = jest.fn();
const mockOnCancel = jest.fn();
const mockOnDelete = jest.fn();

const timeout = {
  enter: -mockMap.size * 20 + 600,
  exit: 100
};

const mountSelection = isOpen => {
  return mount(
    <ThemeProvider>
      <Selection
        isOpen={isOpen}
        selectionId={1}
        instances={mockMap}
        instanceCount={145}
        transitionTimeOut={timeout}
        onToggle={mockOnClick}
        onRetry={mockOnRetry}
        onCancel={mockOnCancel}
        onDelete={mockOnDelete}
      />
    </ThemeProvider>
  );
};

describe('Selection', () => {
  let node;
  let isOpen = true;
  beforeEach(() => {
    node = mountSelection(isOpen);
  });

  it('should contain a Header', () => {
    expect(node.find(Styled.Dt)).toExist();
    expect(node.find(Styled.Dt).props().isExpanded).toBe(true);
  });

  it('should contain Instances', () => {
    expect(node.find('ul')).toExist();
    expect(node.find('ul')).toMatchSnapshot();

    expect(node.find(Styled.StatusCell)).toExist();
    expect(node.find(StateIcon)).toExist();
    expect(node.find(Styled.NameCell)).toExist();
    expect(node.find(Styled.IdCell)).toExist();
    expect(node.find(Styled.ActionStatusCell)).toExist();
    expect(node.find(Styled.InstanceActionStatus)).toExist();
  });

  it('should contain a Footer', () => {
    expect(node.find(Styled.Footer)).toExist();
    expect(node.find(Styled.Footer)).toMatchSnapshot();
    expect(node.find(Styled.MoreInstances)).toExist();
    expect(node.find(Styled.MoreInstances).contains('144 more Instances')).toBe(
      true
    );
  });

  it('should contain Actions', () => {
    expect(node.find(Styled.Actions)).toExist();
    expect(node.find(Styled.Actions)).toMatchSnapshot();
    expect(node.find(Styled.DropdownWrapper)).toExist();
    expect(node.find(Dropdown)).toExist();
    expect(node.find(Styled.DeleteIcon)).toExist();
    expect(node.find(Styled.ActionButton).props().onClick).toBe(mockOnDelete);
  });

  it('should only have Header when closed', () => {
    // when
    let isOpen = false;
    node = mountSelection(isOpen);

    // then
    expect(node.find("[data-test='selection-toggle']")).toExist();
    expect(node.find('dd')).not.toExist();
    expect(node.find('footer')).not.toExist();
    expect(node.find("[data-test='actions']")).not.toExist();
  });

  it('should call the passed toggle method', () => {
    node.find(Styled.SelectionToggle).simulate('click');
    expect(mockOnClick).toHaveBeenCalled();
  });

  it('should call the passed delete method', () => {
    node.find(Styled.ActionButton).simulate('click');
    expect(mockOnDelete).toHaveBeenCalled();
  });

  it('should call the passed retry method', () => {
    node
      .find('button')
      .find("[data-test='dropdown-toggle']")
      .simulate('click');

    node
      .find('[data-test="RESOLVE_INCIDENT-dropdown-option"]')
      .simulate('click');
    expect(mockOnRetry).toHaveBeenCalled();
  });

  it('should call the passed cancel method', () => {
    node
      .find('button')
      .find("[data-test='dropdown-toggle']")
      .simulate('click');

    node
      .find('[data-test="CANCEL_WORKFLOW_INSTANCE-dropdown-option"]')
      .simulate('click');
    expect(mockOnCancel).toHaveBeenCalled();
  });

  it('should pass the loading state to ActionStatus', async () => {
    const node = shallow(
      <Selection
        isOpen={isOpen}
        selectionId={1}
        instances={mockMap}
        instanceCount={145}
        transitionTimeOut={timeout}
        onToggle={mockOnClick}
        onRetry={mockOnRetry}
        onCancel={mockOnCancel}
        onDelete={mockOnDelete}
      />
    );

    node
      .find('[data-test="CANCEL_WORKFLOW_INSTANCE-dropdown-option"]')
      .simulate('click');

    await flushPromises();
    node.update();

    expect(node.find('Styled(ActionStatus)').props().operationState).toBe(
      OPERATION_STATE.SCHEDULED
    );

    // simulate list update
    node.setProps({instances: new Map(mockMap)});
    node.update();

    // remove spinner and show state of last operation
    expect(node.find('Styled(ActionStatus)').props().operationState).not.toBe(
      OPERATION_STATE.SCHEDULED
    );
  });
});
