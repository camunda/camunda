/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

type Props = {
  onCmInput: React.ChangeEventHandler<HTMLInputElement>;
  indeterminate: boolean;
  label?: string;
  id?: string;
  title?: string;
};

const Checkbox: React.FC<Props> = ({
  onCmInput,
  indeterminate,
  label,
  id,
  title,
  ...props
}) => {
  return (
    <label htmlFor={id}>
      {label ?? title}
      <input type="checkbox" onChange={onCmInput} id={id} {...props} />
    </label>
  );
};

export {Checkbox};
