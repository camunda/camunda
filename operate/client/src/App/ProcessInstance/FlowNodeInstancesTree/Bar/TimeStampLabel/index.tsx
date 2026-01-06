/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import {formatDate} from 'modules/utils/date';
import {TimeStamp} from './styled';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';

type Props = {
  timeStamp: null | string;
};

const TimeStampLabel: React.FC<Props> = observer(({timeStamp}) => {
  const {isTimeStampVisible} = flowNodeTimeStampStore.state;

  return isTimeStampVisible && timeStamp ? (
    <TimeStamp>{formatDate(timeStamp)}</TimeStamp>
  ) : null;
});

export {TimeStampLabel};
