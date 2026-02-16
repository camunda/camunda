/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from '@testing-library/react';
import {render, screen, waitFor, type Screen} from 'modules/testing-library';
import {MoveAction} from '..';
import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {
  mockProcessInstancesV2,
  setupSelectionStoreWithInstances,
  getProcessInstance,
  createWrapper,
} from '../../tests/mocks';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {batchModificationStore} from 'modules/stores/batchModification';

const PROCESS_ID = 'MoveModificationProcess';
const mockProcessXML = open('MoveModificationProcess.bpmn');

const waitForDiagramToLoad = async (screen: Screen) => {
  await waitFor(() => {
    const button = screen.getByRole('button', {name: /move/i});
    const title = button.getAttribute('title');
    expect(title).not.toBe('Please select an element from the diagram first.');
  });
};

describe('<MoveAction />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockSearchProcessInstances().withSuccess({
      items: mockProcessInstancesV2,
      page: {totalItems: mockProcessInstancesV2.length},
    });
  });

  it('should disable button when no process version is selected', () => {
    render(<MoveAction />, {
      wrapper: createWrapper({withTestButtons: true}),
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Please select an element from the diagram first.',
    );
  });

  it('should disable button when only finished instances are selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('TERMINATED', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'You can only move element instances in active or incident state.',
    );
  });

  it('should disable button when start event is selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=StartEvent`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button when boundary event is selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=BoundaryEvent`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button when multi instance task is selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MultiInstanceTask`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button if element is attached to event based gateway', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MessageEvent`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Elements attached to an event based gateway are not supported.',
    );
  });

  it('should disable button if element is inside multi instance sub process', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=TaskInsideMultiInstance`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Elements inside a multi instance element are not supported.',
    );
  });

  it('should enable move button when active or incident instances are selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should enable move button when all instances are selected', async () => {
    const {user} = render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should display migration helper modal and enter migration mode', async () => {
    const {user} = render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      screen.getByText(/process instance batch move mode/i),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        /this mode allows you to move multiple instances as a batch in a one operation/i,
      ),
    ).toBeInTheDocument();

    expect(batchModificationStore.state.isEnabled).toBe(false);

    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(batchModificationStore.state.isEnabled).toBe(true);

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();
  });

  it('should hide helper modal after checkbox click', async () => {
    const {user} = render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      screen.getByText(/process instance batch move mode/i),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('checkbox', {
        name: /don't show this message next time/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: /close/i}));

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      screen.queryByText(/process instance batch move mode/i),
    ).not.toBeInTheDocument();

    localStorage.clear();

    await user.click(
      screen.getByRole('button', {name: /exit batch modification mode/i}),
    );

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      screen.getByText(/process instance batch move mode/i),
    ).toBeInTheDocument();
  });

  it('should enable move button when conditional event is selected', async () => {
    render(<MoveAction />, {
      wrapper: createWrapper({
        initialPath: `/processes?process=${PROCESS_ID}&version=1&flowNodeId=ConditionalEvent`,
        withTestButtons: true,
      }),
    });

    await waitForDiagramToLoad(screen);

    setupSelectionStoreWithInstances(mockProcessInstancesV2);

    const instance = getProcessInstance('ACTIVE', mockProcessInstancesV2);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(
        instance.processInstanceKey,
      );
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeEnabled();
  });
});
