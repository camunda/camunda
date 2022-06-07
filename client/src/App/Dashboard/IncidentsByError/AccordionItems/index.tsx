/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from '../utils/getAccordionItemTitle';
import {getAccordionItemLabel} from '../utils/getAccordionItemLabel';
import {truncateErrorMessage} from '../utils/truncateErrorMessage';
import {Ul, Li, InstancesBar} from './styled';
import {PanelListItem} from 'modules/components/PanelListItem';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {Process} from 'modules/stores/incidentsByError';

type Props = {
  errorMessage: string;
  processes: Process[];
};

const AccordionItems: React.FC<Props> = ({errorMessage, processes}) => {
  return (
    <Ul>
      {processes.map((item) => {
        const name = item.name || item.bpmnProcessId;

        return (
          <Li key={item.processId}>
            <PanelListItem
              to={Locations.processes({
                process: item.bpmnProcessId,
                version: item.version.toString(),
                errorMessage: truncateErrorMessage(errorMessage),
                incidents: true,
              })}
              onClick={() => {
                panelStatesStore.expandFiltersPanel();
                tracking.track({
                  eventName: 'navigation',
                  link: 'dashboard-process-incidents-by-error-message-single-version',
                });
              }}
              title={getAccordionItemTitle(
                name,
                item.instancesWithActiveIncidentsCount,
                item.version,
                errorMessage
              )}
              $boxSize="small"
            >
              <InstancesBar
                label={getAccordionItemLabel(name, item.version)}
                incidentsCount={item.instancesWithActiveIncidentsCount}
                barHeight={2}
                size="small"
              />
            </PanelListItem>
          </Li>
        );
      })}
    </Ul>
  );
};

export {AccordionItems};
