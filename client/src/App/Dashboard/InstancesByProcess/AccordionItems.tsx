/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from './utils/getAccordionItemTitle';
import {getAccordionItemLabel} from './utils/getAccordionItemLabel';
import InstancesBar from 'modules/components/InstancesBar';
import {VersionList, VersionLi} from './styled';
import {PanelListItem} from 'modules/components/PanelListItem';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {ProcessInstanceByName} from 'modules/stores/processInstancesByName';

type Props = {
  processName: string;
  processes: ProcessInstanceByName['processes'];
};

const AccordionItems: React.FC<Props> = ({processName, processes}) => {
  return (
    <VersionList>
      {processes.map((process) => {
        const totalInstancesCount =
          process.instancesWithActiveIncidentsCount +
          process.activeInstancesCount;
        return (
          <VersionLi key={process.processId}>
            <PanelListItem
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
                process.version
              )}
              $boxSize="small"
            >
              <InstancesBar
                label={getAccordionItemLabel(
                  process.name || processName,
                  totalInstancesCount,
                  process.version
                )}
                incidentsCount={process.instancesWithActiveIncidentsCount}
                activeCount={process.activeInstancesCount}
                size="small"
                barHeight={3}
              />
            </PanelListItem>
          </VersionLi>
        );
      })}
    </VersionList>
  );
};

export {AccordionItems};
