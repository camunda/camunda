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

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {ProcessInstanceByNameDto} from 'modules/api/incidents/fetchProcessInstancesByName';
import {Li, LinkWrapper} from '../styled';
import {InstancesBar} from 'modules/components/InstancesBar';
import {authenticationStore} from 'modules/stores/authentication';
import {observer} from 'mobx-react';

type Props = {
  processName: string;
  processes: ProcessInstanceByNameDto['processes'];
  tabIndex?: number;
};

const Details: React.FC<Props> = observer(
  ({processName, processes, tabIndex}) => {
    const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;

    return (
      <ul>
        {processes.map((process) => {
          const tenantName =
            authenticationStore.tenantsById?.[process.tenantId] ??
            process.tenantId;

          const totalInstancesCount =
            process.instancesWithActiveIncidentsCount +
            process.activeInstancesCount;
          return (
            <Li key={process.processId}>
              <LinkWrapper
                tabIndex={tabIndex ?? 0}
                to={Locations.processes({
                  process: process.bpmnProcessId,
                  version: process.version.toString(),
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
                        tenant: process.tenantId,
                      }
                    : {}),
                })}
                onClick={() => {
                  panelStatesStore.expandFiltersPanel();
                  tracking.track({
                    eventName: 'navigation',
                    link: 'dashboard-process-instances-by-name-single-version',
                  });
                }}
                title={getAccordionItemTitle({
                  processName: process.name || processName,
                  instancesCount: totalInstancesCount,
                  version: process.version,
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
                    size: 'small',
                    text: getAccordionItemLabel({
                      name: process.name || processName,
                      instancesCount: totalInstancesCount,
                      version: process.version,
                      ...(isMultiTenancyEnabled
                        ? {
                            tenant: tenantName,
                          }
                        : {}),
                    }),
                  }}
                  incidentsCount={process.instancesWithActiveIncidentsCount}
                  activeInstancesCount={process.activeInstancesCount}
                  size="small"
                />
              </LinkWrapper>
            </Li>
          );
        })}
      </ul>
    );
  },
);

export {Details};
