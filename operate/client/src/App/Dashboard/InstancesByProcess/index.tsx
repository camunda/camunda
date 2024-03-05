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

import React from 'react';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {observer} from 'mobx-react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {getAccordionLabel} from './utils/getAccordionLabel';
import {InstancesBar} from 'modules/components/InstancesBar';
import {LinkWrapper, ErrorMessage} from '../styled';
import {Skeleton} from '../PartiallyExpandableDataTable/Skeleton';
import {EmptyState} from 'modules/components/EmptyState';
import {ReactComponent as EmptyStateProcessInstancesByName} from 'modules/components/Icon/empty-state-process-instances-by-name.svg';
import {authenticationStore} from 'modules/stores/authentication';
import {Details} from './Details';
import {generateProcessKey} from 'modules/utils/generateProcessKey';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;

  const modelerLink = authenticationStore.state.c8Links.modeler;
  const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

  if (['initial', 'first-fetch'].includes(status)) {
    return <Skeleton />;
  }

  if (hasNoInstances) {
    return (
      <EmptyState
        icon={
          <EmptyStateProcessInstancesByName title="Start by deploying a process" />
        }
        heading="Start by deploying a process"
        description="There are no processes deployed. Deploy and start a process from our Modeler, then come back here to track its progress."
        link={{
          label: 'Learn more about Operate',
          href: 'https://docs.camunda.io/docs/components/operate/operate-introduction/',
          onClick: () =>
            tracking.track({
              eventName: 'dashboard-link-clicked',
              link: 'operate-docs',
            }),
        }}
        button={
          modelerLink !== undefined
            ? {
                label: 'Go to Modeler',
                href: modelerLink,
                onClick: () =>
                  tracking.track({
                    eventName: 'dashboard-link-clicked',
                    link: 'modeler',
                  }),
              }
            : undefined
        }
      />
    );
  }

  if (status === 'error') {
    return <ErrorMessage />;
  }

  return (
    <PartiallyExpandableDataTable
      dataTestId="instances-by-process"
      headers={[{key: 'instance', header: 'instance'}]}
      rows={processInstances.map((item) => {
        const {
          instancesWithActiveIncidentsCount,
          activeInstancesCount,
          processName,
          bpmnProcessId,
          processes,
          tenantId,
        } = item;
        const name = processName || bpmnProcessId;
        const version = processes.length === 1 ? processes[0]!.version : 'all';
        const totalInstancesCount =
          instancesWithActiveIncidentsCount + activeInstancesCount;
        const tenantName =
          authenticationStore.tenantsById?.[item.tenantId] ?? item.tenantId;

        return {
          id: generateProcessKey(bpmnProcessId, tenantId),
          instance: (
            <LinkWrapper
              to={Locations.processes({
                process: bpmnProcessId,
                version: version.toString(),
                active: true,
                incidents: true,
                ...(totalInstancesCount === 0
                  ? {
                      completed: true,
                      canceled: true,
                    }
                  : {}),
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: tenantId,
                    }
                  : {}),
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-instances-by-name-all-versions',
                });
              }}
              title={getAccordionTitle({
                processName: name,
                instancesCount: totalInstancesCount,
                versionsCount: processes.length,
                ...(isMultiTenancyEnabled
                  ? {
                      tenant: tenantName,
                    }
                  : {}),
              })}
            >
              <InstancesBar
                label={{
                  type: 'process',
                  size: 'medium',
                  text: getAccordionLabel({
                    name,
                    instancesCount: totalInstancesCount,
                    versionsCount: processes.length,
                    ...(isMultiTenancyEnabled
                      ? {
                          tenant: tenantName,
                        }
                      : {}),
                  }),
                }}
                incidentsCount={instancesWithActiveIncidentsCount}
                activeInstancesCount={activeInstancesCount}
                size="medium"
              />
            </LinkWrapper>
          ),
        };
      })}
      expandedContents={processInstances.reduce(
        (accumulator, {bpmnProcessId, tenantId, processName, processes}) => {
          if (processes.length <= 1) {
            return accumulator;
          }

          return {
            ...accumulator,
            [generateProcessKey(bpmnProcessId, tenantId)]: (
              <Details
                processName={processName || bpmnProcessId}
                processes={processes}
              />
            ),
          };
        },
        {},
      )}
    />
  );
});
export {InstancesByProcess};
