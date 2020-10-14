/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState} from 'react';
import classnames from 'classnames';

import {Button, Icon, Dropdown, MultiValueInput} from 'components';
import {t} from 'translation';

import OptionsList from './OptionsList';
import Typeahead from './Typeahead';

import './MultiSelect.scss';

export default function MultiSelect({
  onSearch = () => {},
  onOpen = () => {},
  onClose = () => {},
  values = [],
  noValuesMessage,
  placeholder,
  className,
  onAdd,
  onRemove,
  async,
  onClear,
  typedOption,
  hasMore,
  loading,
  disabled,
  children,
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [optionClicked, setOptionClicked] = useState(false);
  const [resetInput, setResetInput] = useState(false);
  const [insideClick, setInsideClick] = useState(false);

  const input = useRef();

  const isEmpty = !loading && !query && React.Children.count(children) === 0;
  const isInputDisabled = disabled || (isEmpty && !typedOption);

  function showList() {
    if (!open) {
      onOpen(query);
      setOpen(true);
    }
  }

  function hideList() {
    if (!optionClicked) {
      if (query && !insideClick) {
        setResetInput(!resetInput);
        setQuery('');
      }
      onClose();
      setOpen(false);
    } else {
      input.current.focus();
    }
    setInsideClick(false);
  }

  function selectOption({props: {value}}) {
    onAdd(value);
    setOptionClicked(false);
  }

  function getPlaceholderText() {
    if (disabled || isEmpty) {
      return noValuesMessage || t('common.notFound');
    }
    return placeholder;
  }

  function onChange(value) {
    setQuery(value);
    onSearch(value);
  }

  return (
    <div className={classnames('MultiSelect', className)} onMouseDown={() => setInsideClick(true)}>
      <MultiValueInput
        className="typeaheadInput"
        key={resetInput}
        onChange={onChange}
        values={values}
        onAdd={(...args) => {
          if (!optionClicked) {
            onAdd(...args);
          }
        }}
        onRemove={onRemove}
        ref={input}
        onBlur={hideList}
        onClear={onClear}
        onFocus={showList}
        disabled={isInputDisabled}
        placeholder={getPlaceholderText(isEmpty)}
        disableAddByKeyboard
      />
      <Button
        tabIndex="-1"
        className="optionsButton"
        onClick={() => input.current.focus()}
        disabled={isInputDisabled}
      >
        <Icon type="down" className="downIcon" />
      </Button>
      <OptionsList
        async={async}
        open={open}
        onClose={hideList}
        onOpen={open}
        filter={query}
        onSelect={selectOption}
        input={input.current}
        onMouseDown={() => setOptionClicked(true)}
        loading={loading}
        hasMore={hasMore}
        typedOption={typedOption}
      >
        {children}
      </OptionsList>
    </div>
  );
}

MultiSelect.Option = Dropdown.Option;

MultiSelect.Highlight = Typeahead.Highlight;
