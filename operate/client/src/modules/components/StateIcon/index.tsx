/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {WarningFilled, CheckmarkOutline, RadioButtonChecked} from './styled';
import {Icon, Error} from '@carbon/react/icons';

const stateIconsMap = {
  FAILED: WarningFilled,
  INCIDENT: WarningFilled,
  ACTIVE: RadioButtonChecked,
  COMPLETED: CheckmarkOutline,
  EVALUATED: CheckmarkOutline,
  CANCELED: Error,
  TERMINATED: Error,
} as const;

type Props = {
  state: InstanceEntityState | DecisionInstanceEntityState;
  size: React.ComponentProps<Icon>['size'];
};

const StateIcon: React.FC<Props> = ({state, ...props}) => {
  const TargetComponent = stateIconsMap[state];
  return <TargetComponent data-testid={`${state}-icon`} {...props} />;
};

export {StateIcon};
