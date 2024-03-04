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

import {render, screen} from 'modules/testing-library';
import {LastModification} from './index';
import {MemoryRouter} from 'react-router-dom';
import {modificationsStore} from 'modules/stores/modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  createAddVariableModification,
  createEditVariableModification,
} from 'modules/mocks/modifications';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {open} from 'modules/mocks/diagrams';
import {act} from 'react-dom/test-utils';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('LastModification', () => {
  it('should not display last modification if no modifications applied', () => {
    render(<LastModification />, {wrapper: Wrapper});

    expect(
      screen.queryByText(/Last added modification/),
    ).not.toBeInTheDocument();
  });

  it('should display/remove last added modification', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'service-task-1',
        active: 1,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'service-task-2',
        active: 1,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'service-task-3',
        active: 1,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'service-task-4',
        active: 1,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'service-task-5',
        active: 1,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(1);

    const {user} = render(<LastModification />, {wrapper: Wrapper});

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          scopeId: generateUniqueID(),
          flowNode: {id: 'service-task-1', name: 'service-task-1'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(/Last added modification/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Add "service-task-1"/)).toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('service-task-2');
    });

    expect(
      await screen.findByText(/Cancel "service-task-2"/),
    ).toBeInTheDocument();

    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          flowNode: {id: 'service-task-3', name: 'service-task-3'},
          targetFlowNode: {id: 'service-task-4', name: 'service-task-4'},
          affectedTokenCount: 2,
          visibleAffectedTokenCount: 2,
          scopeIds: [generateUniqueID(), generateUniqueID()],
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(/Move "service-task-3" to "service-task-4"/),
    ).toBeInTheDocument();

    act(() => {
      createAddVariableModification({
        id: '1',
        scopeId: '5',
        flowNodeName: 'service-task-5',
        name: 'variableName1',
        value: 'variableValue1',
      });
    });

    expect(
      await screen.findByText(/Add new variable "variableName1"/),
    ).toBeInTheDocument();

    act(() => {
      createEditVariableModification({
        name: 'variableName2',
        oldValue: 'variableValue2',
        newValue: 'editedVariableValue2',
        scopeId: '5',
        flowNodeName: 'flowNode6',
      });
    });

    expect(
      await screen.findByText(/Edit variable "variableName2"/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Add new variable "variableName1"/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Move "service-task-3" to "service-task-4"/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Cancel "service-task-2"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Add "service-task-1"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.queryByText(/Last added modification/),
    ).not.toBeInTheDocument();
  });
});
