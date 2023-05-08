/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef} from 'react';
import {Field, useField} from 'react-final-form';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {formatDate, formatTime} from './formatDate';
import {Calendar} from '@carbon/react/icons';
import {DateRangeModal} from './DateRangeModal';
import {IconTextInput} from '../IconTextInput';

type Props = {
  filterName: string;
  popoverTitle: string;
  label: string;
  fromDateTimeKey: string;
  toDateTimeKey: string;
  isModalOpen: boolean;
  onModalClose: () => void;
  onClick: () => void;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
  if (fromDateTime === undefined || toDateTime === undefined) {
    return '';
  }
  return `${formatDate(fromDateTime)} ${formatTime(
    fromDateTime
  )} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = observer(
  ({
    filterName,
    popoverTitle,
    label,
    fromDateTimeKey,
    toDateTimeKey,
    isModalOpen,
    onModalClose,
    onClick,
  }) => {
    const textFieldRef = useRef<HTMLDivElement>(null);
    const fromDateTime = useField<string>(fromDateTimeKey).input.value;
    const toDateTime = useField<string>(toDateTimeKey).input.value;

    const getInputValue = () => {
      if (isModalOpen) {
        return 'Custom';
      }
      if (fromDateTime !== '' && toDateTime !== '') {
        return formatInputValue(new Date(fromDateTime), new Date(toDateTime));
      }
      return '';
    };

    return (
      <>
        <div ref={textFieldRef}>
          <IconTextInput
            Icon={Calendar}
            id={`optional-filter-${filterName}`}
            labelText={label}
            value={getInputValue()}
            title={getInputValue()}
            placeholder="Enter date range"
            size="sm"
            onClick={() => {
              if (!isModalOpen) {
                onClick();
                tracking.track({
                  eventName: 'date-range-popover-opened',
                  filterName,
                });
              }
            }}
          />
          {[fromDateTimeKey, toDateTimeKey].map((filterKey) => (
            <Field
              name={filterKey}
              key={filterKey}
              component="input"
              type="hidden"
            />
          ))}
        </div>

        <DateRangeModal
          isModalOpen={isModalOpen}
          title={popoverTitle}
          filterName={filterName}
          onCancel={onModalClose}
          onApply={() => {
            onModalClose();
          }}
        />
      </>
    );
  }
);

export {DateRangeField};
