/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionTitle} from './utils/getAccordionTitle';
import {getAccordionLabel} from './utils/getAccordionLabel';
import InstancesBar from 'modules/components/InstancesBar';
import {PanelListItem} from 'modules/components/PanelListItem';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {ProcessInstanceByName} from 'modules/stores/processInstancesByName';

type Props = {
  item: ProcessInstanceByName;
  version: 'all' | number;
};

const Accordion: React.FC<Props> = ({item, version}) => {
  const {instancesWithActiveIncidentsCount, activeInstancesCount} = item;

  const name = item.processName || item.bpmnProcessId;
  const totalInstancesCount =
    instancesWithActiveIncidentsCount + activeInstancesCount;

  return (
    <PanelListItem
      to={Locations.processes({
        process: item.bpmnProcessId,
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
        item.processes.length
      )}
    >
      <InstancesBar
        label={getAccordionLabel(
          name,
          totalInstancesCount,
          item.processes.length
        )}
        incidentsCount={item.instancesWithActiveIncidentsCount}
        activeCount={item.activeInstancesCount}
        size="medium"
        barHeight={5}
      />
    </PanelListItem>
  );
};

export {Accordion};
