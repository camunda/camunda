/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {formatDate} from 'modules/utils/date';

import {TimeStamp} from './styled';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';

const TimeStampLabel = observer(({timeStamp, isSelected}) => {
  const {isTimeStampVisible} = flowNodeTimeStamp.state;

  return isTimeStampVisible && timeStamp ? (
    <TimeStamp isSelected={isSelected}>{formatDate(timeStamp)}</TimeStamp>
  ) : null;
});

TimeStampLabel.propTypes = {
  timeStamp: PropTypes.string,
  isSelected: PropTypes.bool.isRequired,
};

export {TimeStampLabel};
