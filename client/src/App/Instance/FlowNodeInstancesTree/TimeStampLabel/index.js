/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {formatDate} from 'modules/utils/date';

import * as Styled from './styled';
import {flowNodeTimeStamp} from 'modules/stores/flowNodeTimeStamp';
import {observer} from 'mobx-react';

const TimeStampLabel = observer(
  class TimeStampLabel extends React.PureComponent {
    static propTypes = {
      timeStamp: PropTypes.string,
      isSelected: PropTypes.bool.isRequired,
    };

    render() {
      const {timeStamp, isSelected} = this.props;
      const {isTimeStampVisible} = flowNodeTimeStamp.state;

      return isTimeStampVisible && timeStamp ? (
        <Styled.TimeStamp isSelected={isSelected}>
          {formatDate(timeStamp)}
        </Styled.TimeStamp>
      ) : null;
    }
  }
);

export {TimeStampLabel};
