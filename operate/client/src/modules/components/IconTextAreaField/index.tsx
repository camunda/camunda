/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useFieldError} from 'modules/hooks/useFieldError';
import {IconTextArea} from '../IconInput';

type Props = React.ComponentProps<typeof IconTextArea> & {
  name: string;
};

const IconTextAreaField: React.FC<Props> = ({name, ...props}) => {
  const error = useFieldError(name);

  return (
    <IconTextArea
      {...props}
      invalid={error !== undefined}
      invalidText={error}
    />
  );
};
export {IconTextAreaField};
