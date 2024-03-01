/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {act, render, screen} from 'modules/testing-library';
import {ModificationIcons} from './index';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {useEffect} from 'react';
import {open} from 'modules/mocks/diagrams';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return modificationsStore.reset;
  }, []);

  return <>{children}</>;
};

describe('<ModificationIcons />', () => {
  beforeEach(() => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'parent_sub_process',
        active: 3,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'inner_sub_process',
        active: 3,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'user_task',
        active: 3,
        incidents: 0,
        completed: 0,
        canceled: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));
  });

  it('should show correct icons for modifications planning to be added', () => {
    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'some-flow-node-id',
          isPlaceholder: true,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByTitle(
        'Ensure to add/edit variables if required, input/output mappings are not executed during modification',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByTitle('This flow node instance is planned to be added'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if all the running tokens on the flow node is canceled', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'user_task',
          isPlaceholder: false,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.queryByTitle('This flow node instance is planned to be canceled'),
    ).not.toBeInTheDocument();

    act(() => modificationsStore.cancelAllTokens('user_task'));

    expect(
      screen.getByTitle('This flow node instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the running tokens on the flow node is canceled', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'user_task',
          isPlaceholder: false,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() => modificationsStore.cancelToken('user_task', 'some-flow-node-id'));

    expect(
      screen.getByTitle('This flow node instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should not show modification planned to be canceled icon if one of the other running tokens on the flow node is canceled', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'user_task',
          isPlaceholder: false,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken('user_task', 'some-other-flow-node-id'),
    );

    expect(
      screen.queryByTitle('This flow node instance is planned to be canceled'),
    ).not.toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the parent running tokens on the flow node is canceled', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'user_task',
          isPlaceholder: false,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken('user_task', 'some-parent-flow-node-id'),
    );

    expect(
      screen.getByTitle('This flow node instance is planned to be canceled'),
    ).toBeInTheDocument();
  });

  it('should show modification planned to be canceled icon if one of the other parent running tokens on the flow node is canceled', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    render(
      <ModificationIcons
        flowNodeInstance={{
          flowNodeId: 'user_task',
          isPlaceholder: false,
          endDate: null,
          treePath:
            'some-other-parent-flow-node-id/some-parent-flow-node-id/some-flow-node-id',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    act(() =>
      modificationsStore.cancelToken(
        'user_task',
        'some-second-parent-flow-node-id',
      ),
    );

    expect(
      screen.queryByTitle('This flow node instance is planned to be canceled'),
    ).not.toBeInTheDocument();
  });
});
