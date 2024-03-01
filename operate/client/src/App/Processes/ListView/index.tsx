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

import {InstancesList} from '../../Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {DiagramPanel} from './DiagramPanel';
import {observer} from 'mobx-react';
import {useEffect} from 'react';
import {processesStore} from 'modules/stores/processes/processes.list';
import {deleteSearchParams} from 'modules/utils/filter';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {PAGE_TITLE} from 'modules/constants';
import {notificationsStore} from 'modules/stores/notifications';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {reaction} from 'mobx';
import {tracking} from 'modules/tracking';
import {OperationsPanel} from 'modules/components/OperationsPanel';
import {batchModificationStore} from 'modules/stores/batchModification';

type LocationType = Omit<Location, 'state'> & {
  state: {refreshContent?: boolean};
};

const ListView: React.FC = observer(() => {
  const location = useLocation() as LocationType;
  const navigate = useNavigate();

  const filters = getProcessInstanceFilters(location.search);
  const {process, tenant} = filters;
  const {
    state: {status: processesStatus},
    isInitialLoadComplete,
  } = processesStore;
  const filtersJSON = JSON.stringify(filters);

  useEffect(() => {
    if (
      processesStore.state.status !== 'initial' &&
      location.state?.refreshContent
    ) {
      processesStore.fetchProcesses();
    }
  }, [location.state]);

  useEffect(() => {
    processInstancesSelectionStore.init();
    processInstancesStore.init();
    processesStore.fetchProcesses();

    document.title = PAGE_TITLE.INSTANCES;

    return () => {
      processInstancesSelectionStore.reset();
      processInstancesStore.reset();
      processesStore.reset();
    };
  }, []);

  useEffect(() => {
    processInstancesSelectionStore.resetState();
  }, [filtersJSON]);

  useEffect(() => {
    if (isInitialLoadComplete && !location.state?.refreshContent) {
      processInstancesStore.fetchProcessInstancesFromFilters();
    }
  }, [location.search, isInitialLoadComplete, location.state]);

  useEffect(() => {
    if (isInitialLoadComplete && location.state?.refreshContent) {
      processInstancesStore.fetchProcessInstancesFromFilters();
    }
  }, [isInitialLoadComplete, location.state]);

  useEffect(() => {
    const disposer = reaction(
      () => variableFilterStore.state.variable,
      () => {
        if (processesStatus === 'fetched') {
          tracking.track({
            eventName: 'process-instances-filtered',
            filterName: 'variable',
            multipleValues: variableFilterStore.state.isInMultipleMode,
          });
          processInstancesStore.fetchProcessInstancesFromFilters();
        }
      },
    );

    return disposer;
  }, [processesStatus]);

  useEffect(() => {
    if (processesStatus === 'fetched') {
      if (
        process !== undefined &&
        processesStore.getProcess({
          bpmnProcessId: process,
          tenantId: tenant,
        }) === undefined
      ) {
        navigate(deleteSearchParams(location, ['process', 'version']));
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Process could not be found',
          isDismissable: true,
        });
      }
    }
  }, [process, tenant, navigate, processesStatus, location]);

  return (
    <>
      <VisuallyHiddenH1>Operate Process Instances</VisuallyHiddenH1>
      <InstancesList
        type="process"
        leftPanel={<Filters />}
        topPanel={<DiagramPanel />}
        bottomPanel={<InstancesTable />}
        rightPanel={<OperationsPanel />}
        frame={{
          isVisible: batchModificationStore.state.isEnabled,
          headerTitle: 'Batch Modification Mode',
        }}
      />
    </>
  );
});

export {ListView};
