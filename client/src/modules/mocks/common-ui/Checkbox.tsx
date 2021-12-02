/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

type Props = {
  onCmInput: React.ChangeEventHandler<HTMLInputElement>;
  indeterminate: boolean;
};

const Checkbox: React.FC<Props> = ({onCmInput, indeterminate, ...props}) => {
  return <input type="checkbox" onChange={onCmInput} {...props} />;
};

export {Checkbox};
