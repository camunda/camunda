/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {act} from '@testing-library/react';
import {render, screen} from 'modules/testing-library';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';
import {
  fetchProcessInstances,
  fetchProcessXml,
  getProcessInstance,
  getWrapper,
} from '../../mocks';
import {MoveAction} from '..';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';
import {batchModificationStore} from 'modules/stores/batchModification';

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=StartEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=BoundaryEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MultiInstanceTask`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=MessageEvent`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=TaskInsideMultiInstance`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

    const instance = getProcessInstance('ACTIVE', mockProcessInstances);

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should enable move button when all instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    expect(screen.getByRole('button', {name: /move/i})).toBeDisabled();

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    expect(screen.getByRole('button', {name: /move/i})).toBeEnabled();
  });

  it('should display migration helper modal and enter migration mode', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<MoveAction />, {
      wrapper: getWrapper(
        `/processes?process=${PROCESS_ID}&version=1&flowNodeId=Task`,
      ),
    });

    await fetchProcessInstances(screen, user);
    await fetchProcessXml(screen, user);

    await user.click(
      screen.getByRole('button', {name: /select all instances/i}),
    );

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      screen.getByText(/process instance batch move mode/i),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('checkbox', {name: /do not show this message again/i}),
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
