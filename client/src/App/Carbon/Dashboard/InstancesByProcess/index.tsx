/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {processInstancesByNameStore} from 'modules/stores/processInstancesByName';
import {observer} from 'mobx-react';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable';
import {CarbonLocations} from 'modules/carbonRoutes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {getAccordionTitle} from './utils/getAccordionTitle';
import {getAccordionLabel} from './utils/getAccordionLabel';
import {InstancesBar} from 'modules/components/Carbon/InstancesBar';
import {LinkWrapper} from '../styled';

const InstancesByProcess: React.FC = observer(() => {
  const {
    state: {processInstances, status},
    hasNoInstances,
  } = processInstancesByNameStore;

  if (['initial', 'first-fetch'].includes(status)) {
    return <div>skeleton</div>;
  }

  if (hasNoInstances) {
    return <div>empty state</div>;
  }

  if (status === 'error') {
    return <div>error state</div>;
  }

  return (
    <PartiallyExpandableDataTable
      headers={[{key: 'instance', header: 'instance'}]}
      rows={processInstances.map((item) => {
        const {
          instancesWithActiveIncidentsCount,
          activeInstancesCount,
          processName,
          bpmnProcessId,
          processes,
        } = item;
        const name = processName || bpmnProcessId;
        const version = processes[0]!.version;
        const totalInstancesCount =
          instancesWithActiveIncidentsCount + activeInstancesCount;

        return {
          id: bpmnProcessId,
          instance: (
            <LinkWrapper
              to={CarbonLocations.processes({
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
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-instances-by-name-all-versions',
                });
              }}
              title={getAccordionTitle(
                name,
                totalInstancesCount,
                processes.length,
              )}
            >
              <InstancesBar
                label={{
                  type: 'process',
                  size: 'medium',
                  text: getAccordionLabel(
                    name,
                    totalInstancesCount,
                    processes.length,
                  ),
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
        (accumulator, {bpmnProcessId, processName, processes}) => {
          if (processes.length <= 1) {
            return accumulator;
          }

          return {
            ...accumulator,
            [bpmnProcessId]: <div>{processName || bpmnProcessId}</div>,
          };
        },
        {},
      )}
    />
  );
});
export {InstancesByProcess};
