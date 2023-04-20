/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  useState,
  useEffect,
  useCallback,
  ReactNode,
  ReactElement,
  MouseEvent,
  isValidElement,
  cloneElement,
  createRef,
  Children,
} from 'react';

import {t} from 'translation';
import {Dropdown, LoadingIndicator} from 'components';

import {highlightText} from './service';

import './OptionsList.scss';

export interface OptionProps {
  label: string;
  value: string;
  children: ReactNode;
  disabled?: boolean;
  className: string;
  onClick: () => void;
  onMouseDown?: (evt: MouseEvent<HTMLDivElement, MouseEvent>) => void;
}

interface OptionsListProps {
  loading?: boolean;
  hasMore?: boolean;
  input: HTMLInputElement | null;
  onSelect: (option: ReactElement<OptionProps>) => void;
  filter?: string;
  children: ReactNode;
  onMouseDown?: (evt: MouseEvent<HTMLDivElement, MouseEvent>) => void;
  async?: boolean;
  typedOption?: boolean;
  open: boolean;
  onOpen: () => void;
  onClose: () => void;
}

export default function OptionsList({
  loading,
  hasMore,
  input,
  onSelect,
  filter,
  children,
  onMouseDown,
  async,
  typedOption,
  ...props
}: OptionsListProps): JSX.Element | null {
  const [selectedOption, setSelectedOption] = useState<number>(-1);
  const optionList = createRef<HTMLDivElement>();
  const optionsArr = Children.toArray(children).filter(isValidElement<OptionProps>);
  let filteredOptions: ReactElement<OptionProps>[] = optionsArr;

  if (!async && filter) {
    filteredOptions = optionsArr.filter(({props: {label, children}}) => {
      if (filter && typedOption && (label || children) === filter) {
        return false; // remove options that exactly match the typed option
      }
      return (label || children)?.toString().toLowerCase().includes(filter.toLowerCase());
    });
  }

  if (filter && typedOption) {
    filteredOptions.unshift(
      <Dropdown.Option key={filter} value={filter} label={filter}>
        {filter}
      </Dropdown.Option>
    );
  }

  const optionsWithProps = filteredOptions.map((option, i) =>
    cloneElement(option, {
      className: i === selectedOption ? 'isActive' : '',
      onClick: () => onSelect(option),
      onMouseDown: (evt: MouseEvent<HTMLDivElement, MouseEvent>) =>
        !option.props.disabled && onMouseDown?.(evt),
      children: highlightText(option.props.children, filter),
    })
  );

  const handleKeyPress = useCallback(
    (evt: KeyboardEvent) => {
      const {open, onOpen, onClose} = props;
      let nextOption = -1;
      evt = evt || window.event;

      const optionsCount = filteredOptions.length;

      if (evt.key === 'Enter') {
        evt.stopPropagation();
        const optionToSelect = filteredOptions[selectedOption];
        if (optionToSelect && !optionToSelect.props.disabled) {
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

        // scroll to end on the last element to show the has more info message
        if (nextOption === optionsCount - 1) {
          optionList.current.scrollTop = optionList.current.scrollHeight;
        }
      }

      setSelectedOption(nextOption);
    },
    [selectedOption, onSelect, filteredOptions, optionList, props]
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

  const isEmpty = filteredOptions.length === 0 && !loading && !typedOption;

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
