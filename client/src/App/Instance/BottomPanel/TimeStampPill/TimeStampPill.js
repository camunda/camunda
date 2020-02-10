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
  const [isTreeLoaded, setIsTreeLoaded] = useState(false);
  const [isDefLoaded, setIsDefLoaded] = useState(false);
  const {subscribe, unsubscribe} = useDataManager();

  useEffect(() => {
    subscribe(SUBSCRIPTION_TOPIC.LOAD_INSTANCE_TREE, LOADING_STATE.LOADED, () =>
      setIsTreeLoaded(true)
    );
    subscribe(
      SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS,
      LOADING_STATE.LOADED,
      () => setIsDefLoaded(true)
    );
    return () => unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isDisabled = !isTreeLoaded && !isDefLoaded;

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
