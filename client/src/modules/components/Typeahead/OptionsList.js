/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useCallback} from 'react';
import {t} from 'translation';
import {Dropdown} from 'components';

export default function OptionsList({input, onSelect, filter, children, onMouseDown, ...props}) {
  const [selectedOption, setSelectedOption] = useState(-1);
  const optionList = React.createRef();
  const optionsArr = React.Children.toArray(children);

  const filteredOptions = optionsArr.filter(({props: {label, children}}) =>
    (label || children).toLowerCase().includes(filter.toLowerCase())
  );

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

  return (
    <div ref={optionList} className="OptionsList">
      {optionsWithProps}
      {filteredOptions.length === 0 && (
        <Dropdown.Option className="message">{t('common.notFound')}</Dropdown.Option>
      )}
    </div>
  );
}
