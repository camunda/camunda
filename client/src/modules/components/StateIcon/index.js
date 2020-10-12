/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {STATE} from 'modules/constants';
import {themed} from 'modules/theme';

import * as Styled from './styled';

const stateIconsMap = {
  [STATE.INCIDENT]: Styled.IncidentIcon,
  [STATE.ACTIVE]: Styled.ActiveIcon,
  [STATE.COMPLETED]: Styled.CompletedIcon,
  [STATE.CANCELED]: Styled.CanceledIcon,
  [STATE.TERMINATED]: Styled.CanceledIcon,
};

function StateIcon({state, ...props}) {
  const TargetComponent = stateIconsMap[state] || Styled.AliasIcon;
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
}

export default themed(StateIcon);

StateIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(STATE)).isRequired,
  theme: PropTypes.string.isRequired,
};
