/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {PILL_TYPE} from 'modules/constants';
import * as Styled from './styled';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';

const TimeStampPill = observer(function TimeStampPill() {
  const {isInitialLoadComplete: isTreeLoaded} = flowNodeInstance.state;
  const {
    isInitialLoadComplete: areDefinitionsLoaded,
  } = singleInstanceDiagram.state;
  const {
    state: {isTimeStampVisible},
    toggleTimeStampVisibility,
  } = flowNodeTimeStamp;

  const isDisabled = !isTreeLoaded && !areDefinitionsLoaded;
  return (
    <Styled.Pill
      isActive={isTimeStampVisible}
      onClick={toggleTimeStampVisibility}
      type={PILL_TYPE.TIMESTAMP}
      isDisabled={isDisabled}
    >
      {`${isTimeStampVisible ? 'Hide' : 'Show'} End Time`}
    </Styled.Pill>
  );
});

export {TimeStampPill};
