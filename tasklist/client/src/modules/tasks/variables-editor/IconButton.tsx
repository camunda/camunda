/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton as BaseIconButton} from '@carbon/react';

type Props = {
  onClick?: React.ComponentProps<typeof BaseIconButton>['onClick'];
  label: React.ComponentProps<typeof BaseIconButton>['label'];
  align?: React.ComponentProps<typeof BaseIconButton>['align'];
  children: React.ReactNode;
  className?: string;
};

const IconButton: React.FC<Props> = ({
  onClick,
  label,
  children,
  align = 'left',
  className,
}) => {
  return (
    <BaseIconButton
      align={align}
      size="sm"
      kind="ghost"
      label={label}
      onClick={onClick}
      className={className}
      leaveDelayMs={100}
    >
      {children}
    </BaseIconButton>
  );
};

export {IconButton};
