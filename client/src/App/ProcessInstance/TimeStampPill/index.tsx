/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {PILL_TYPE} from 'modules/constants';
import {Container, Pill} from './styled';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {tracking} from 'modules/tracking';

const TimeStampPill = observer(function TimeStampPill() {
  const {status: flowNodeInstanceStatus} = flowNodeInstanceStore.state;
  const {status: diagramStatus} = processInstanceDetailsDiagramStore.state;
  const {
    state: {isTimeStampVisible},
    toggleTimeStampVisibility,
  } = flowNodeTimeStampStore;

  const isDisabled =
    flowNodeInstanceStatus !== 'fetched' && diagramStatus !== 'fetched';
  return (
    <Container>
      <Pill
        isActive={isTimeStampVisible}
        onClick={() => {
          toggleTimeStampVisibility();
          tracking.track({eventName: 'instance-history-end-time-toggled'});
        }}
        type={PILL_TYPE.TIMESTAMP}
        isDisabled={isDisabled}
      >
        {`${isTimeStampVisible ? 'Hide' : 'Show'} End Date`}
      </Pill>
    </Container>
  );
});

export {TimeStampPill};
