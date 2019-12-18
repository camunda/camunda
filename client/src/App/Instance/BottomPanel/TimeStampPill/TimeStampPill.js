/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import PropTypes from 'prop-types';

import {PILL_TYPE, LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';
import useDataManager from 'modules/hooks/useDataManager';
import {withFlowNodeTimeStampContext} from 'modules/contexts/FlowNodeTimeStampContext';

import * as Styled from './styled';

function TimeStampPill(props) {
  const {showTimeStamp, onTimeStampToggle} = props;
  const [isDisabled, setDisabled] = useState(true);
  const {subscribe} = useDataManager();

  useEffect(() => {
    subscribe(SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE, LOADING_STATE.LOADED, () =>
      setDisabled(false)
    );
  }, []);

  return (
    <Styled.Pill
      isActive={showTimeStamp}
      onClick={onTimeStampToggle}
      type={PILL_TYPE.TIMESTAMP}
      isDisabled={isDisabled}
    >
      {`${showTimeStamp ? 'Hide' : 'Show'} End Time`}
    </Styled.Pill>
  );
}

TimeStampPill.propTypes = {
  onTimeStampToggle: PropTypes.func.isRequired,
  showTimeStamp: PropTypes.bool.isRequired
};

export default withFlowNodeTimeStampContext(TimeStampPill);
