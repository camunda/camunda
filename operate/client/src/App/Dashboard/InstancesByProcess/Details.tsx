/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import type {ProcessInstanceByNameDto} from 'modules/api/incidents/fetchProcessInstancesByName';
import {Li, LinkWrapper} from '../styled';
import {InstancesBar} from 'modules/components/InstancesBar';
import {observer} from 'mobx-react';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

type Props = {
  processName: string;
  processes: ProcessInstanceByNameDto['processes'];
  tabIndex?: number;
};

const Details: React.FC<Props> = observer(
  ({processName, processes, tabIndex}) => {
    const isMultiTenancyEnabled = window.clientConfig?.multiTenancyEnabled;
    const {data: currentUser} = useCurrentUser();
    const tenantsById: Record<string, string> =
      currentUser?.tenants.reduce(
        (acc, tenant) => ({
          [tenant.tenantId]: tenant.name,
          ...acc,
        }),
        {},
      ) ?? {};

    return (
      <ul>
        {processes.map((process) => {
          const tenantName = tenantsById[process.tenantId] ?? process.tenantId;

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
