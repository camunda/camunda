/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {logger} from 'modules/logger';

import {useFieldError} from 'modules/hooks/useFieldError';

type Props = {
  children?: React.ReactNode;
  name: string;
};

const FinalFormFieldWrapper: React.FC<Props> = ({children, name}) => {
  const error = useFieldError(name);

  if (React.isValidElement(children)) {
    return React.cloneElement(children, {
      'aria-invalid': error !== undefined,
    });
  }

  logger.error('No valid child element provided for FinalFormFieldWrapper');
  return null;
};

export {FinalFormFieldWrapper};
