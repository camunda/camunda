/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  children?: React.ReactNode;
  type?: 'RUNNING_INSTANCES' | 'FILTERS' | 'INCIDENTS' | 'SELECTIONS';
  $isActive?: boolean;
  position?: number;
};

export default function Badge(props: Props) {
  const {children, position = 0, $isActive = true} = props;

  const isRoundBadge =
    children && children.toString().length === 1 && position === 0;
  const Component = isRoundBadge ? Styled.BadgeCircle : Styled.Badge;

  return <Component data-testid="badge" $isActive={$isActive} {...props} />;
}
