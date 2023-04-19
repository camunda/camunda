/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {LastModification} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('LastModification', () => {
  it('should not display last modification if no modifications applied', () => {
    render(<LastModification />, {wrapper: Wrapper});

    expect(
      screen.queryByText(/Last added modification/)
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
      'processInstanceId'
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
      await screen.findByText(/Last added modification/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Add "service-task-1"/)).toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('service-task-2');
    });

    expect(
      await screen.findByText(/Cancel "service-task-2"/)
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
      await screen.findByText(/Move "service-task-3" to "service-task-4"/)
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
      await screen.findByText(/Add new variable "variableName1"/)
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
      await screen.findByText(/Edit variable "variableName2"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Add new variable "variableName1"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.getByText(/Move "service-task-3" to "service-task-4"/)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Cancel "service-task-2"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(screen.getByText(/Add "service-task-1"/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Undo'}));

    expect(
      screen.queryByText(/Last added modification/)
    ).not.toBeInTheDocument();
  });
});
