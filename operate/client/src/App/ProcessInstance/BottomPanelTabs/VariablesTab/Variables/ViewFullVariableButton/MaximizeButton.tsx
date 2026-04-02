/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import type {MaximizeButtonProps} from './types';

const MaximizeButton: React.FC<MaximizeButtonProps> = ({onClick, label}) => {
  return (
    <Button
      kind="ghost"
      hasIconOnly
      renderIcon={Maximize}
      size="sm"
      aria-label={label}
      iconDescription={label}
      tooltipPosition="left"
      onClick={onClick}
    />
  );
};

export {MaximizeButton};
