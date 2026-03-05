/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Copy} from '@carbon/react/icons';
import {
  hasItems,
  isPaginated,
  isTruncated,
  variablesAsJSON,
} from 'modules/utils/variables';
import {useVariables} from 'modules/queries/variables/useVariables';
import {writeToClipboard} from './writeToClipboard';

const CopyVariablesButton: React.FC = () => {
  const {data} = useVariables();

  const getErrorMessage = () => {
    if (isPaginated(data)) {
      return 'Copying is disabled for 50 variables or more';
    }

    if (isTruncated(data)) {
      return 'Copying is disabled for variable values larger than 8192 characters';
    }
  };

  const isDisabled = isPaginated(data) || isTruncated(data) || !hasItems(data);

  return (
    <Button
      kind="ghost"
      size="md"
      disabled={isDisabled}
      title={getErrorMessage() ?? 'Click to copy variables to clipboard'}
      onClick={() => {
        writeToClipboard(variablesAsJSON(data));
      }}
      renderIcon={Copy}
    >
      Copy variables
    </Button>
  );
};

export {CopyVariablesButton};
