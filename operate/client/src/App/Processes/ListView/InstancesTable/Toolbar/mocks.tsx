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

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {MemoryRouter} from 'react-router-dom';
import {Screen, waitFor} from '@testing-library/react';
import {Paths} from 'modules/Routes';
import {ProcessInstancesDto} from 'modules/api/processInstances/fetchProcessInstances';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {UserEvent} from '@testing-library/user-event';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {batchModificationStore} from 'modules/stores/batchModification';

const fetchProcessInstances = async (screen: Screen, user: UserEvent) => {
  await user.click(
    screen.getByRole('button', {name: /fetch process instances/i}),
  );
  await waitFor(() =>
    expect(processInstancesStore.state.status).toBe('fetched'),
  );
};

const fetchProcessXml = async (screen: Screen, user: UserEvent) => {
  await user.click(screen.getByRole('button', {name: /fetch process xml/i}));
  await waitFor(() =>
    expect(processXmlStore.state.diagramModel).not.toBeNull(),
  );
};

const getProcessInstance = (
  state: ProcessInstanceEntity['state'],
  mockData: ProcessInstancesDto,
) => {
  const instance = mockData.processInstances.find(
    (instance) => instance.state === state,
  );

  if (instance === undefined) {
    throw new Error(`please make sure there is a ${state} in mockData`);
  }

  return instance;
};

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
    ({children}) => {
      useEffect(() => {
        return () => {
          processInstancesSelectionStore.reset();
          processInstancesStore.reset();
          processInstanceMigrationStore.reset();
          processStatisticsStore.reset();
          processXmlStore.reset();
          batchModificationStore.reset();
        };
      }, []);
      return (
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <button
            onClick={processInstancesSelectionStore.selectAllProcessInstances}
          >
            Select all instances
          </button>
          <button
            onClick={() =>
              processInstancesStore.fetchInstances({
                fetchType: 'initial',
                payload: {query: {}},
              })
            }
          >
            Fetch process instances
          </button>
          <button
            onClick={() => {
              processXmlStore.fetchProcessXml('1');
            }}
          >
            Fetch process xml
          </button>
          <button onClick={batchModificationStore.enable}>
            Enter batch modification mode
          </button>
          <button onClick={batchModificationStore.disable}>
            Exit batch modification mode
          </button>
        </MemoryRouter>
      );
    },
  );

  return Wrapper;
}

export {getWrapper, getProcessInstance, fetchProcessInstances, fetchProcessXml};
