/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactElement, useEffect, useRef, useState, Children, ReactNode} from 'react';
import classnames from 'classnames';

import {Button, Icon, Dropdown, UncontrolledMultiValueInput} from 'components';
import {t} from 'translation';

import OptionsList, {OptionProps} from './OptionsList';
import Typeahead from './Typeahead';

import './MultiSelect.scss';

type MultiSelectProps = {
  onSearch?: (query: string) => void;
  onOpen?: (query: string) => void;
  onClose?: () => void;
  values?: {label: string; value: string}[];
  noValuesMessage?: string;
  placeholder?: ReactNode;
  className?: string;
  onAdd: (value: string) => void;
  onRemove: (value: string, index: number) => void;
  async?: boolean;
  onClear: () => void;
  typedOption?: boolean;
  hasMore?: boolean;
  loading?: boolean;
  disabled?: boolean;
  children: ReactNode;
  persistMenu?: boolean;
};

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
  persistMenu = true,
}: MultiSelectProps): JSX.Element {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [insideClick, setInsideClick] = useState(false);

  const input = useRef<HTMLInputElement>(null);

  const isEmpty = !loading && !query && Children.count(children) === 0;
  const isInputDisabled = disabled || (isEmpty && !typedOption);

  useEffect(() => {
    if (isInputDisabled) {
      setOpen(false);
    }
  }, [isInputDisabled]);

  function showList() {
    if (!open) {
      onOpen(query);
      setOpen(true);
    }
  }

  function hideList() {
    if (!insideClick) {
      onClose();
      setOpen(false);
    } else {
      // wait for input to blur before focusing again
      setTimeout(() => input.current?.focus(), 0);
    }
    setInsideClick(false);
  }

  function selectOption({props: {value}}: ReactElement<OptionProps>) {
    if (query) {
      setQuery('');
      onSearch('');
    }
    if (!persistMenu) {
      hideList();
    }
    onAdd(value);
  }

  function getPlaceholderText() {
    if (disabled || isEmpty) {
      return noValuesMessage || t('common.notFound');
    }
    return placeholder;
  }

  function onChange({target: {value}}: React.ChangeEvent<HTMLInputElement>) {
    setQuery(value);
    onSearch(value);
    showList();
  }

  function handleKeyPress(evt: React.KeyboardEvent<HTMLInputElement>) {
    if (query === '' && evt.key === 'Backspace' && values.length > 0) {
      const lastElementIndex = values.length - 1;
      const lastElementValue = values[lastElementIndex]?.value;
      if (lastElementValue) {
        onRemove(lastElementValue, lastElementIndex);
      }
    }
  }

  return (
    <div
      className={classnames('MultiSelect', className)}
      onMouseDown={() => setInsideClick(true)}
      onMouseUp={() => setInsideClick(false)}
    >
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
        placeholder={getPlaceholderText()}
        onKeyDown={handleKeyPress}
      />
      <Button
        tabIndex={-1}
        className="optionsButton"
        onClick={() => input.current?.focus()}
        disabled={isInputDisabled}
      >
        <Icon type="down" className="downIcon" />
      </Button>
      <OptionsList
        async={async}
        open={open}
        onClose={hideList}
        onOpen={showList}
        filter={query}
        onSelect={selectOption}
        input={input.current}
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
