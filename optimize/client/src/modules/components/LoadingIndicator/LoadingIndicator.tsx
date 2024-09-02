/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentPropsWithoutRef} from 'react';
import classnames from 'classnames';

import './LoadingIndicator.scss';

interface LoadingIndicatorProps extends ComponentPropsWithoutRef<'div'> {
  small?: boolean;
}

export default function LoadingIndicator({
  small,
  className,
  ...props
}: LoadingIndicatorProps): JSX.Element {
  return <div {...props} className={classnames('LoadingIndicator', className, {small})} />;
}
