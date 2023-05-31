/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
