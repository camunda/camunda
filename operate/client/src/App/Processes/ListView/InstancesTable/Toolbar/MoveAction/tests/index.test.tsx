/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from '@testing-library/react';
import {render, screen} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';
import {
  fetchProcessInstances,
  getProcessInstance,
  getWrapper,
} from '../../mocks';
import {MoveAction} from '..';
import {open} from 'modules/mocks/diagrams';
import {batchModificationStore} from 'modules/stores/batchModification';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const PROCESS_DEFINITION_ID = '2251799813685249';
const PROCESS_ID = 'MoveModificationProcess';
const mockProcessXML = open('MoveModificationProcess.bpmn');

jest.mock('modules/stores/processes/processes.list', () => ({
  processesStore: {
    getPermissions: jest.fn(),
    state: {processes: []},
    versionsByProcessAndTenant: {
      [`{${PROCESS_ID}}-{<default>}`]: [
        {id: PROCESS_DEFINITION_ID, version: 1},
      ],
    },
  },
}));

describe('<MoveAction />', () => {
  it('should disable button when no process version is selected', () => {
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<MoveAction />, {wrapper: getWrapper()});

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Please select an element from the diagram first.',
    );
  });

  it('should disable button when only finished instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('CANCELED', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'You can only move flow node instances in active or incident state.',
    );
  });

  it('should disable button when start event is selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=StartEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button when boundary event is selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=BoundaryEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button when multi instance task is selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MultiInstanceTask`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'The selected element type is not supported.',
    );
  });

  it('should disable button if element is attached to event based gateway', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MessageEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Elements attached to an event based gateway are not supported.',
    );
  });

  it('should disable button if element is inside multi instance sub process', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=TaskInsideMultiInstance`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    const moveButton = screen.getByRole('button', {name: /move/i});

    expect(moveButton).toBeDisabled();
    expect(moveButton).toHaveAttribute(
      'title',
      'Elements inside a multi instance element are not supported.',
    );
  });

  it('should enable move button when active or incident instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should enable move button when all instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    await fetchProcessInstances(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should display migration helper modal and enter migration mode', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);

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
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);

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
});
