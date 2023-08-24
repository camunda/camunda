/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import {Locations} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {ProcessInstanceByNameDto} from 'modules/api/incidents/fetchProcessInstancesByName';
import {Li, LinkWrapper} from '../styled';
import {InstancesBar} from 'modules/components/InstancesBar';

type Props = {
  processName: string;
  processes: ProcessInstanceByNameDto['processes'];
  tabIndex?: number;
};

const Details: React.FC<Props> = ({processName, processes, tabIndex}) => {
  return (
    <ul>
      {processes.map((process) => {
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
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-instances-by-name-single-version',
                });
              }}
              title={getAccordionItemTitle(
                process.name || processName,
                totalInstancesCount,
                process.version,
              )}
            >
              <InstancesBar
                label={{
                  type: 'process',
                  size: 'small',
                  text: getAccordionItemLabel(
                    process.name || processName,
                    totalInstancesCount,
                    process.version,
                  ),
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
};

export {Details};
