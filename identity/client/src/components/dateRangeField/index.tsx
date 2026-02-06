/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
import {formatDate, formatISODate, formatTime} from './formatDate';
import {Calendar} from '@carbon/react/icons';
import {DateRangeModal} from './DateRangeModal';
import {IconTextInput} from './IconTextInput';

type Props = {
  filterName: string;
  popoverTitle: string;
  label: string;
  fromDateTime?: Date;
  toDateTime?: Date;
  onApply: (fromDateTime: Date, toDateTime: Date) => void;
  isModalOpen: boolean;
  onModalClose: () => void;
  onClick: () => void;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
  if (fromDateTime === undefined || toDateTime === undefined) {
    return '';
  }
  return `${formatDate(fromDateTime)} ${formatTime(
    fromDateTime,
  )} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = ({
  filterName,
  popoverTitle,
  label,
  fromDateTime,
  toDateTime,
  onApply,
  isModalOpen,
  onModalClose,
  onClick,
}) => {
  const textFieldRef = useRef<HTMLDivElement>(null);

  const getInputValue = () => {
    if (isModalOpen) {
      return 'Custom';
    }
    if (fromDateTime && toDateTime) {
      return formatInputValue(fromDateTime, toDateTime);
    }
    return '';
  };

  const handleClick = () => {
    if (!isModalOpen) {
      onClick();
    }
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
          size="md"
          buttonLabel="Open date range modal"
          onIconClick={handleClick}
          onClick={handleClick}
          readOnly
        />
      </div>

      {isModalOpen ? (
        <DateRangeModal
          isModalOpen={isModalOpen}
          title={popoverTitle}
          filterName={filterName}
          onCancel={onModalClose}
          onApply={({fromDateTime, toDateTime}) => {
            onModalClose();
            onApply(fromDateTime, toDateTime);
          }}
          defaultValues={{
            fromDate:
              fromDateTime === undefined ? '' : formatDate(fromDateTime),
            fromTime:
              fromDateTime === undefined ? '' : formatTime(fromDateTime),
            toDate: toDateTime === undefined ? '' : formatDate(toDateTime),
            toTime: toDateTime === undefined ? '' : formatTime(toDateTime),
          }}
          key={`date-range-modal-${isModalOpen ? 'open' : 'closed'}`}
        />
      ) : null}
    </>
  );
};

export {DateRangeField};

