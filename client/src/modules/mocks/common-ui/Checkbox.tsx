/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useImperativeHandle, useState} from 'react';

type Props = {
  onCmInput: (event: {detail: {isChecked: boolean}}) => {};
  indeterminate: boolean;
  label?: string;
  id?: string;
  title?: string;
};

const Checkbox = React.forwardRef<
  {renderValidity: () => Promise<void>; checked: boolean},
  Props
>(({onCmInput, indeterminate, label, id, title, ...props}, ref) => {
  const [isChecked, setIsChecked] = useState<boolean>(false);

  useImperativeHandle(ref, () => ({
    renderValidity: () => Promise.resolve(),
    checked: isChecked,
  }));

  return (
    <label htmlFor={id}>
      {label ?? title}
      <input
        type="checkbox"
        onChange={(event) => {
          setIsChecked(event.target.checked);
          onCmInput?.({detail: {isChecked: event.target.checked}});
        }}
        id={id}
        checked={isChecked}
        {...props}
      />
    </label>
  );
});

export {Checkbox};
