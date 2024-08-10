/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Icon,
  Error,
  CheckmarkOutline,
  RadioButtonChecked,
  WarningFilled,
} from '@carbon/react/icons';
import {match} from 'ts-pattern';
import {ProcessInstance} from 'modules/types';
import styles from './styles.module.scss';
import cn from 'classnames';

type Props = React.ComponentProps<Icon> & {
  state: ProcessInstance['state'];
};

const ProcessInstanceStateIcon: React.FC<Props> = ({
  state,
  className,
  ...rest
}) => {
  return match({state})
    .with({state: 'incident'}, () => (
      <WarningFilled
        {...rest}
        data-testid="incident-icon"
        className={cn(className, styles.error)}
      />
    ))
    .with({state: 'active'}, () => (
      <RadioButtonChecked
        {...rest}
        data-testid="active-icon"
        className={cn(className, styles.success)}
      />
    ))
    .with({state: 'completed'}, () => (
      <CheckmarkOutline
        {...rest}
        data-testid="completed-icon"
        className={cn(className, styles.secondary)}
      />
    ))
    .with({state: 'canceled'}, () => (
      <Error {...rest} data-testid="canceled-icon" className={className} />
    ))
    .with({state: 'terminated'}, () => (
      <Error {...rest} data-testid="terminated-icon" className={className} />
    ))
    .exhaustive();
};

export {ProcessInstanceStateIcon};
