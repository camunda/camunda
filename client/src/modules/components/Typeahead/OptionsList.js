/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useCallback} from 'react';
import {t} from 'translation';
import {Dropdown, LoadingIndicator} from 'components';

export default function OptionsList({
  loading,
  hasMore,
  input,
  onSelect,
  filter,
  children,
  onMouseDown,
  async,
  ...props
}) {
  const [selectedOption, setSelectedOption] = useState(-1);
  const optionList = React.createRef();
  const optionsArr = React.Children.toArray(children);
  let filteredOptions = optionsArr;

  if (!async) {
    filteredOptions = optionsArr.filter(({props: {label, children}}) =>
      (label || children).toLowerCase().includes(filter.toLowerCase())
    );
  }

  const optionsWithProps = filteredOptions.map((option, i) =>
    React.cloneElement(option, {
      className: i === selectedOption ? 'isActive' : '',
      onClick: evt => onSelect(option),
      onMouseDown
    })
  );

  const handleKeyPress = useCallback(
    evt => {
      const {open, onOpen, onClose} = props;
      let nextOption = -1;
      evt = evt || window.event;

      const optionsCount = optionsWithProps.length;

      if (evt.key === 'Enter') {
        evt.stopPropagation();
        const optionToSelect = optionsWithProps[selectedOption];
        if (optionToSelect) {
          onSelect(optionToSelect);
        }
        return;
      }

      if (evt.key === 'Escape') {
        return onClose();
      }

      if (evt.key === 'ArrowDown') {
        if (!open) {
          return onOpen();
        }
        nextOption = (selectedOption + 1) % optionsCount;
      }

      if (evt.key === 'ArrowUp') {
        nextOption = selectedOption - 1 < 0 ? optionsCount - 1 : selectedOption - 1;
      }

      if (optionList.current) {
        const selectedItem = optionList.current.querySelectorAll('.DropdownOption')[nextOption];
        if (selectedItem) {
          selectedItem.scrollIntoView({block: 'nearest', inline: 'nearest'});
        }
      }

      // scroll to end on the last element to show the has more info message
      if (nextOption === optionsCount - 1) {
        optionList.current.scrollTop = optionList.current.scrollHeight;
      }

      setSelectedOption(nextOption);
    },
    [selectedOption, onSelect, optionsWithProps, optionList, props]
  );

  useEffect(() => {
    if (!input) {
      return;
    }
    input.addEventListener('keydown', handleKeyPress);
    return () => {
      input.removeEventListener('keydown', handleKeyPress);
    };
  }, [handleKeyPress, input]);

  if (!props.open) {
    return null;
  }

  const isEmpty = filteredOptions.length === 0 && !loading;

  return (
    <div ref={optionList} className="OptionsList">
      {loading ? <LoadingIndicator /> : optionsWithProps}
      {isEmpty && <Dropdown.Option className="message">{t('common.notFound')}</Dropdown.Option>}
      {hasMore && (
        <Dropdown.Option className="message">
          {t('common.searchForMore', {count: optionsWithProps.length})}
        </Dropdown.Option>
      )}
    </div>
  );
}
