/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Copy} from '@carbon/react/icons';
import {useVariablesContext} from '../../../VariablePanel/v2/VariablesContext';
import {
  hasItems,
  isPaginated,
  isTruncated,
  variablesAsJSON,
} from 'modules/utils/variables';

const CopyVariablesButton: React.FC = () => {
  const {variablesData} = useVariablesContext();

  const getErrorMessage = () => {
    if (isPaginated(variablesData)) {
      return 'Copying is disabled for 50 variables or more';
    }

    if (isTruncated(variablesData)) {
      return 'Copying is disabled for variable values larger than 8192 characters';
    }
  };

  const isDisabled =
    isPaginated(variablesData) ||
    isTruncated(variablesData) ||
    !hasItems(variablesData);

  return (
    <Button
      kind="ghost"
      size="md"
      disabled={isDisabled}
      title={getErrorMessage() ?? 'Click to copy variables to clipboard'}
      onClick={() => {
        navigator.clipboard.writeText(variablesAsJSON(variablesData));
      }}
      renderIcon={Copy}
    >
      Copy variables
    </Button>
  );
};

export {CopyVariablesButton};
