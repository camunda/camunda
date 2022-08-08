/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useState} from 'react';
import {Field, useForm} from 'react-final-form';
import {DateRangePopover} from './DateRangePopover';
import {TextField} from './styled';

type Props = {
  label: string;
  fromDateKey: string;
  toDateKey: string;
};

const DateRangeField: React.FC<Props> = ({label, fromDateKey, toDateKey}) => {
  const cmTextFieldRef = useRef<HTMLCmTextfieldElement>(null);
  const textFieldRef = useRef<HTMLDivElement>(null);
  const form = useForm();
  const [isDateRangePopoverVisible, setIsDateRangePopoverVisible] =
    useState<boolean>(false);
  const [inputValue, setInputValue] = useState('');

  const showPopover = () => {
    if (
      form.getFieldState(fromDateKey)?.value === undefined &&
      form.getFieldState(toDateKey)?.value === undefined
    ) {
      setInputValue('Custom');
    }
    setIsDateRangePopoverVisible(true);
  };

  return (
    <>
      <div ref={textFieldRef}>
        <TextField
          label={label}
          type="button"
          fieldSuffix={{
            type: 'icon',
            icon: 'calendar',
            press: () => {},
          }}
          value={inputValue}
          ref={cmTextFieldRef}
          readonly
          title={inputValue}
          onCmClick={() => {
            if (!isDateRangePopoverVisible) {
              showPopover();
            }
          }}
        />

        {[fromDateKey, toDateKey].map((filterKey) => (
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
          initialValues={{
            fromDate: form.getFieldState(fromDateKey)?.value ?? '',
            toDate: form.getFieldState(toDateKey)?.value ?? '',
          }}
          onCancel={() => setIsDateRangePopoverVisible(false)}
          onApply={({fromDate, toDate}) => {
            setIsDateRangePopoverVisible(false);
            setInputValue(`${fromDate} - ${toDate}`);
            form.change(fromDateKey, fromDate);
            form.change(toDateKey, toDate);
          }}
          onOutsideClick={(event) => {
            if (
              event.target instanceof Element &&
              cmTextFieldRef.current?.contains(event.target)
            ) {
              event.stopPropagation();
              event.preventDefault();
            }
            setIsDateRangePopoverVisible(false);
          }}
        />
      )}
    </>
  );
};

export {DateRangeField};
