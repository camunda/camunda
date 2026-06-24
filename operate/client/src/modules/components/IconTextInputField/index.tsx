/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useFieldError} from 'modules/hooks/useFieldError';
import {IconTextInput} from '../IconInput';

type Props = React.ComponentProps<typeof IconTextInput> & {
  name: string;
};

const IconTextInputField: React.FC<Props> = ({name, ...props}) => {
  const error = useFieldError(name);

  return (
    <IconTextInput
      {...props}
      invalid={error !== undefined}
      invalidText={error}
    />
  );
};
export {IconTextInputField};
