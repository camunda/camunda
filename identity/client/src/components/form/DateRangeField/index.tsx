/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useRef } from "react";
import { formatDate, formatTime } from "./formatDate";
import { Calendar } from "@carbon/react/icons";
import { DateRangeModal } from "./DateRangeModal";
import { IconTextInput } from "../IconInput";

type Props = {
  popoverTitle: string;
  label: string;
  isModalOpen: boolean;
  onModalClose: () => void;
  onClick: () => void;
  value: { from: string; to: string };
  onChange: ([fromDateTime, toDateTime]: [Date, Date]) => void;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
  if (fromDateTime === undefined || toDateTime === undefined) {
    return "";
  }
  return `${formatDate(fromDateTime)} ${formatTime(
    fromDateTime,
  )} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = ({
  popoverTitle,
  label,
  isModalOpen,
  onModalClose,
  onClick,
  onChange,
  value,
}) => {
  const textFieldRef = useRef<HTMLDivElement>(null);

  const getInputValue = () => {
    if (isModalOpen) {
      return "Custom";
    }
    if (value.from !== "" && value.to !== "") {
      return formatInputValue(new Date(value.from), new Date(value.to));
    }
    return "";
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
          id={`optional-filter-timestamp`}
          labelText={label}
          value={getInputValue()}
          title={getInputValue()}
          placeholder="Enter date range"
          size="sm"
          buttonLabel="Open date range modal"
          onIconClick={handleClick}
          onClick={handleClick}
        />
      </div>

      {isModalOpen ? (
        <DateRangeModal
          isModalOpen={isModalOpen}
          title={popoverTitle}
          onCancel={onModalClose}
          onApply={({ fromDateTime, toDateTime }) => {
            onModalClose();
            onChange([fromDateTime, toDateTime]);
          }}
          defaultValues={{
            fromDate: value.from === "" ? "" : formatDate(new Date(value.from)),
            fromTime: value.from === "" ? "" : formatTime(new Date(value.from)),
            toDate: value.to === "" ? "" : formatDate(new Date(value.to)),
            toTime: value.to === "" ? "" : formatTime(new Date(value.to)),
          }}
          key={`date-range-modal-${isModalOpen ? "open" : "closed"}`}
        />
      ) : null}
    </>
  );
};

export { DateRangeField };
