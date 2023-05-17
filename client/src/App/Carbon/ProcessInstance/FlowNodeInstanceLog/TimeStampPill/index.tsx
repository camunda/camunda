/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {tracking} from 'modules/tracking';
import {Toggle} from './styled';

const TimeStampPill: React.FC = observer(() => {
  const {status: flowNodeInstanceStatus} = flowNodeInstanceStore.state;
  const {status: diagramStatus} = processInstanceDetailsDiagramStore.state;
  const {
    state: {isTimeStampVisible},
    toggleTimeStampVisibility,
  } = flowNodeTimeStampStore;

  const isDisabled =
    flowNodeInstanceStatus !== 'fetched' && diagramStatus !== 'fetched';
  return (
    <Toggle
      aria-label={`${isTimeStampVisible ? 'Hide' : 'Show'} End Date`}
      id="toggle-end-date"
      labelA="Show End Date"
      labelB="Hide End Date"
      onClick={() => {
        toggleTimeStampVisibility();
        tracking.track({eventName: 'instance-history-end-time-toggled'});
      }}
      disabled={isDisabled}
      size="sm"
    />
  );
});

export {TimeStampPill};
