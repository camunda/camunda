/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import EmptyStateProcessInstancesByName from 'modules/components/Icon/empty-state-process-instances-by-name.svg?react';
import {Details} from './Details';
import {generateProcessKey} from 'modules/utils/generateProcessKey';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {useAvailableTenants} from 'modules/queries/useAvailableTenants';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;
  const {data: currentUser} = useCurrentUser();

  const modelerLink = Array.isArray(currentUser?.c8Links)
    ? currentUser?.c8Links.find((link) => link.name === 'modeler')?.link
    : undefined;
  const tenantsById = useAvailableTenants();
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
        const tenantName = tenantsById[item.tenantId] ?? item.tenantId;

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
