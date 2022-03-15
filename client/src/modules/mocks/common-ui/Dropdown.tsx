/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {CmDropdown} from '@camunda-cloud/common-ui-react';
import React, {useState} from 'react';

type Props = {
  trigger: {type: 'label'; label: string};
  options: React.ComponentProps<typeof CmDropdown>['options'];
  'data-testid'?: string;
};

const Dropdown: React.FC<Props> = ({
  trigger,
  options,
  'data-testid': dataTestId,
  ...props
}) => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  if (options === undefined) {
    return null;
  }

  return (
    <>
      <button {...props} type="button" onClick={() => setIsDropdownOpen(true)}>
        {trigger.label}
      </button>
      {isDropdownOpen && (
        <>
          <ul data-testid={dataTestId}>
            {options[0].options?.map((option, index) => {
              return (
                <li
                  key={index}
                  onClick={() => {
                    if (option.handler !== undefined) {
                      option.handler({preventDismissal: () => false});
                      setIsDropdownOpen(false);
                    }
                  }}
                >
                  {option.label}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </>
  );
};

export {Dropdown};
