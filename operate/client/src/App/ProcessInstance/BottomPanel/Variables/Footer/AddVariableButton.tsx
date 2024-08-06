/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Add} from '@carbon/react/icons';

type Props = {
  disabled?: boolean;
  onClick: () => void;
  className?: string;
};

const AddVariableButton: React.FC<Props> = ({
  disabled = false,
  onClick,
  className,
}) => {
  return (
    <Button
      kind="ghost"
      size="md"
      onClick={onClick}
      disabled={disabled}
      className={className}
      renderIcon={Add}
    >
      Add Variable
    </Button>
  );
};

export {AddVariableButton};
