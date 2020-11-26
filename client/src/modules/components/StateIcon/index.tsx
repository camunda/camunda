/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {STATE} from 'modules/constants';
import {InstanceState} from 'modules/types';

import * as Styled from './styled';

const stateIconsMap = {
  [STATE.INCIDENT]: Styled.IncidentIcon,
  [STATE.ACTIVE]: Styled.ActiveIcon,
  [STATE.COMPLETED]: Styled.CompletedIcon,
  [STATE.CANCELED]: Styled.CanceledIcon,
  [STATE.TERMINATED]: Styled.CanceledIcon,
};

type Props = {
  state?: InstanceState;
};

function StateIcon({state, ...props}: Props) {
  const TargetComponent =
    state === undefined ? Styled.AliasIcon : stateIconsMap[state];

  // @ts-expect-error
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
}

export default StateIcon;
