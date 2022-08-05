/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MouseEventHandler, useRef, useState} from 'react';
import {Field} from 'react-final-form';
import {DateRangePopover} from './DateRangePopover';
import {TextField} from './styled';

type Props = {
  label: string;
  filterKeys: string[];
};

const DateRangeField: React.FC<Props> = ({label, filterKeys}) => {
  const cmTextFieldRef = useRef<HTMLCmTextfieldElement>(null);
  const textFieldRef = useRef<HTMLDivElement>(null);

  const [isDateRangePopoverVisible, setIsDateRangePopoverVisible] =
    useState<boolean>(false);
  const [inputValue, setInputValue] = useState('');

  const handleClick: MouseEventHandler = (event) => {
    if (!isDateRangePopoverVisible) {
      event?.stopPropagation();
      showPopover();
    }
  };

  const showPopover = () => {
    setInputValue('Custom');
    cmTextFieldRef.current?.forceFocus();
    setIsDateRangePopoverVisible(true);
  };

  return (
    <>
      <div ref={textFieldRef}>
        <TextField
          label={label}
          type="text"
          fieldSuffix={{
            type: 'icon',
            icon: 'calendar',
            press: showPopover,
          }}
          value={inputValue}
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
      </div>

      {isDateRangePopoverVisible && textFieldRef.current !== null && (
        <DateRangePopover
          referenceElement={textFieldRef.current}
          onCancel={() => setIsDateRangePopoverVisible(false)}
          onApply={() => setIsDateRangePopoverVisible(false)}
        />
      )}
    </>
  );
};

export {DateRangeField};
