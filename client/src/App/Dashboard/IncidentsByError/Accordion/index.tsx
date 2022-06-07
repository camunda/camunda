/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionTitle} from '../utils/getAccordionTitle';
import {truncateErrorMessage} from '../utils/truncateErrorMessage';
import {InstancesBar} from './styled';
import {PanelListItem} from 'modules/components/PanelListItem';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';

type Props = {
  errorMessage: string;
  instancesWithErrorCount: number;
};

const Accordion: React.FC<Props> = ({
  errorMessage,
  instancesWithErrorCount,
}) => {
  return (
    <PanelListItem
      to={Locations.processes({
        errorMessage: truncateErrorMessage(errorMessage),
        incidents: true,
      })}
      onClick={() => {
        panelStatesStore.expandFiltersPanel();
        tracking.track({
          eventName: 'navigation',
          link: 'dashboard-process-incidents-by-error-message-all-processes',
        });
      }}
      title={getAccordionTitle(instancesWithErrorCount, errorMessage)}
    >
      <InstancesBar
        label={errorMessage}
        incidentsCount={instancesWithErrorCount}
        size="medium"
        barHeight={2}
      />
    </PanelListItem>
  );
};

export {Accordion};
