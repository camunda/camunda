/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState} from 'react';
import classnames from 'classnames';

import {Button, Icon, Dropdown, UncontrolledMultiValueInput} from 'components';
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

  function onChange({target: {value}}) {
    setQuery(value);
    onSearch(value);
  }

  function handleKeyPress(evt) {
    if (query === '' && evt.key === 'Backspace' && values.length > 0) {
      const lastElementIndex = values.length - 1;
      onRemove(values[lastElementIndex].value, lastElementIndex);
    }
  }

  return (
    <div className={classnames('MultiSelect', className)} onMouseDown={() => setInsideClick(true)}>
      <UncontrolledMultiValueInput
        inputClassName="typeaheadInput"
        ref={input}
        value={query}
        onChange={onChange}
        values={values}
        onRemove={onRemove}
        onBlur={hideList}
        onClear={onClear}
        onFocus={showList}
        disabled={isInputDisabled}
        placeholder={getPlaceholderText(isEmpty)}
        onKeyDown={handleKeyPress}
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
