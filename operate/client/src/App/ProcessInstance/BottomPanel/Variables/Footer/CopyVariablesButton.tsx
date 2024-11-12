/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Copy} from '@carbon/react/icons';
import {copyVariablesStore} from 'modules/stores/variables/copyVariables';

const CopyVariablesButton: React.FC = () => {
  const {isPaginated, isTruncated, hasItems, variablesAsJSON} =
    copyVariablesStore;

  const getErrorMessage = () => {
    if (isPaginated) {
      return 'Copying is disabled for 50 variables or more';
    }

    if (isTruncated) {
      return 'Copying is disabled for variable values larger than 8192 characters';
    }
  };

  const isDisabled = isPaginated || isTruncated || !hasItems;

  return (
    <Button
      kind="ghost"
      size="md"
      disabled={isDisabled}
      title={getErrorMessage() ?? 'Click to copy variables to clipboard'}
      onClick={() => {
        navigator.clipboard.writeText(variablesAsJSON);
      }}
      renderIcon={Copy}
    >
      Copy variables
    </Button>
  );
};

export {CopyVariablesButton};
