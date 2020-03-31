/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {formatDate} from 'modules/utils/date';
import {withFlowNodeTimeStampContext} from 'modules/contexts/FlowNodeTimeStampContext';

import * as Styled from './styled';

class TimeStampLabel extends React.PureComponent {
  static propTypes = {
    showTimeStamp: PropTypes.bool.isRequired,
    timeStamp: PropTypes.string,
    isSelected: PropTypes.bool.isRequired,
  };

  render() {
    const {showTimeStamp, timeStamp, isSelected} = this.props;
    return showTimeStamp && timeStamp ? (
      <Styled.TimeStamp isSelected={isSelected}>
        {formatDate(timeStamp)}
      </Styled.TimeStamp>
    ) : null;
  }
}
export default withFlowNodeTimeStampContext(TimeStampLabel);
