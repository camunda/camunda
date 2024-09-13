/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEvent, FocusEvent, forwardRef, useContext, useEffect, useRef, useState} from 'react';
import {Search as CarbonSearch} from '@carbon/react';
// TODO: find a fix for this cause it breaks unit tests
import {MenuContext} from '@carbon/react/es/components/Menu/MenuContext';
import {useMergedRefs} from '@carbon/react/lib/internal/useMergedRefs';

import {useSelectContext} from './Select';

import './Search.scss';

interface SearchProps {
  labelText?: string;
  placeholder?: string;
}

export const Search = forwardRef<HTMLLIElement, SearchProps>(function MenuItemSearch(
  {labelText, placeholder},
  forwardRef
) {
  const context = useContext(MenuContext);
  const menuItemRef = useRef<HTMLLIElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const ref = useMergedRefs<HTMLLIElement>([forwardRef, menuItemRef]);
  const {children, setFilteredChildren} = useSelectContext();
  const [search, setSearch] = useState('');

  useEffect(() => {
    context.dispatch({
      type: 'registerItem',
      payload: {
        ref: menuItemRef,
        disabled: false,
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSearch(e: ChangeEvent<HTMLInputElement>): void {
    // We do this to not trigger the main onChange event on the parent menu
    if ('stopPropagation' in e && typeof e.stopPropagation === 'function') {
      e.stopPropagation();
    }

    const value = e.target.value;
    setSearch(value);
    setFilteredChildren(
      children.filter((child) => {
        if (child.type === Search || !value) {
          return true;
        }

        return (
          child.props.label?.toLowerCase().includes(value.toLowerCase()) ||
          child.props.value?.toLowerCase().includes(value.toLowerCase())
        );
      })
    );
  }

  function handleFocus(e: FocusEvent<HTMLLIElement>) {
    if (e.target === menuItemRef.current) {
      searchRef.current?.focus();
    }
  }

  return (
    <li
      ref={ref}
      className="Search cds--menu-item"
      tabIndex={-1}
      role="menuitem"
      onFocus={handleFocus}
    >
      <CarbonSearch
        ref={searchRef}
        size="sm"
        value={search}
        onChange={(e) => {
          const event = e as ChangeEvent<HTMLInputElement>;
          handleSearch(event);
        }}
        labelText={labelText}
        placeholder={placeholder}
      />
    </li>
  );
});
