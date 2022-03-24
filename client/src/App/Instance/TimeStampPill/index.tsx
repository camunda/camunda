/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {PILL_TYPE} from 'modules/constants';
import {Container, Pill} from './styled';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

const TimeStampPill = observer(function TimeStampPill() {
  const {status: flowNodeInstanceStatus} = flowNodeInstanceStore.state;
  const {status: diagramStatus} = singleInstanceDiagramStore.state;
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
        }}
        type={PILL_TYPE.TIMESTAMP}
        isDisabled={isDisabled}
      >
        {`${isTimeStampVisible ? 'Hide' : 'Show'} End Time`}
      </Pill>
    </Container>
  );
});

export {TimeStampPill};
