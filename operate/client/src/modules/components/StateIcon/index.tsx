/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {WarningFilled, CheckmarkOutline, RadioButtonChecked} from './styled';
import {type Icon, Error} from '@carbon/react/icons';
import type {InstanceEntityState} from 'modules/types/operate';
import type {DecisionInstanceState} from '@camunda/camunda-api-zod-schemas/8.8';

const stateIconsMap = {
  FAILED: WarningFilled,
  INCIDENT: WarningFilled,
  ACTIVE: RadioButtonChecked,
  COMPLETED: CheckmarkOutline,
  EVALUATED: CheckmarkOutline,
  CANCELED: Error,
  TERMINATED: Error,
} as const satisfies Record<Props['state'], unknown>;

type Props = {
  state: InstanceEntityState | DecisionInstanceState;
  size: React.ComponentProps<Icon>['size'];
};

const StateIcon: React.FC<Props> = ({state, ...props}) => {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
};

export {StateIcon};
