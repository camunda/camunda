/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef} from 'react';
import {Field} from 'react-final-form';
import {TextField} from './styled';

type Props = {
  label: string;
  filterKeys: string[];
};

const DateRange: React.FC<Props> = ({label, filterKeys}) => {
  const cmTextFieldRef = useRef<HTMLCmTextfieldElement | null>(null);

  const handleClick = () => {
    cmTextFieldRef.current?.forceFocus();
  };

  return (
    <>
      <TextField
        label={label}
        type="text"
        fieldSuffix={{
          type: 'icon',
          icon: 'calendar',
          press: handleClick,
        }}
        ref={cmTextFieldRef}
        onClick={handleClick}
        readonly
      />
      {filterKeys.map((filterKey) => (
        <Field
          name={filterKey}
          key={filterKey}
          component="input"
          type="hidden"
        />
      ))}
    </>
  );
};

export {DateRange};
