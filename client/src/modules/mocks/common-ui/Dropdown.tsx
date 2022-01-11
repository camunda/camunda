/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {CmDropdown} from '@camunda-cloud/common-ui-react';
import React, {useState} from 'react';

type Props = {
  trigger: React.ComponentProps<typeof CmDropdown>['trigger'];
  options: React.ComponentProps<typeof CmDropdown>['options'];
};

const Dropdown: React.FC<Props> = ({trigger, options, ...props}) => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  if (options === undefined) {
    return null;
  }

  return (
    <div {...props} onClick={() => setIsDropdownOpen(true)}>
      {isDropdownOpen && (
        <>
          <ul>
            {options[0].options?.map((option, index) => {
              return (
                <li
                  key={index}
                  onClick={() => {
                    if (option.handler !== undefined) {
                      option.handler({preventDismissal: () => false});
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
    </div>
  );
};

export {Dropdown};
