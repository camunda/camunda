/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  BaseSyntheticEvent,
  Children,
  ComponentProps,
  createContext,
  ReactElement,
  useContext,
  useEffect,
  useState,
} from 'react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';

import {ignoreFragments} from 'services';
import classnames from 'classnames';
import {t} from 'translation';

import {Search} from './Search';
import {Submenu} from './Submenu';
import {Option} from './Option';

export const SelectContext = createContext<{
  children: ReactElement[];
  filteredChildren: ReactElement[];
  setFilteredChildren: (children: ReactElement[]) => void;
  onChange?: (value: any) => void;
  value?: any;
}>({
  children: [],
  filteredChildren: [],
  setFilteredChildren: () => {},
});

export function useSelectContext() {
  if (!SelectContext) {
    throw new Error('Select compound components cannot be rendered outside the Select component');
  }
  return useContext(SelectContext);
}

export interface SelectProps<T extends object | string | number = string>
  extends Omit<ComponentProps<typeof MenuDropdown>, 'label' | 'onChange'> {
  id: string;
  value?: T;
  onChange?: (value: T) => void;
  labelText?: string | JSX.Element[];
  helperText?: string | JSX.Element[];
  placeholder?: string;
}

export default function Select<T extends object | string | number>(props: SelectProps<T>) {
  const {labelText, helperText, ...rest} = props;
  const childrenArray = ignoreFragments(props.children);
  const [filteredChildren, setFilteredChildren] = useState(childrenArray);

  useEffect(() => {
    setFilteredChildren(ignoreFragments(props.children));
  }, [props.children]);

  const getLabel = (children = props.children) => {
    let label;

    Children.forEach(ignoreFragments(children), (child) => {
      if (child.props.value === props.value) {
        label = child.props.label;
      } else if (child.type === Select.Submenu && child.props.children) {
        const sublabel = getLabel(child.props.children);
        if (sublabel) {
          label = child.props.label + ' : ' + sublabel;
        }
      }
    });

    return label;
  };

  const onChange = (evt: BaseSyntheticEvent) => {
    const value = (evt.target as HTMLElement | null)
      ?.closest('[value]')
      ?.getAttribute('value') as T | null;
    if (value) {
      props.onChange?.(value);
    }
  };

  return (
    <SelectContext.Provider
      value={{
        children: childrenArray,
        filteredChildren,
        setFilteredChildren,
        onChange,
        value: props.value,
      }}
    >
      <div className={classnames('Select', props.className)}>
        {labelText && (
          <div className="cds--text-input__label-wrapper">
            <label htmlFor={props.id} className="cds--label">
              {labelText}
            </label>
          </div>
        )}
        <MenuDropdown
          {...rest}
          onChange={onChange}
          label={props.placeholder || getLabel() || t('common.select').toString()}
          menuTarget={document.querySelector<HTMLElement>('.fullscreen')}
        >
          {filteredChildren}
        </MenuDropdown>
        {helperText && !props.invalid && <div className="cds--form__helper-text">{helperText}</div>}
      </div>
    </SelectContext.Provider>
  );
}

Select.Submenu = Submenu;

Select.Option = Option;

Select.Search = Search;
