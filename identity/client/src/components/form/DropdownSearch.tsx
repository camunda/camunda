/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useEffect, useMemo, useState } from "react";
import { Search } from "@carbon/react";
import ListBox from "@carbon/react/es/components/ListBox";
import useDebounce from "react-debounced";
import { SecondaryText } from "src/components/form/Text";
import styled from "styled-components";

type DropdownSearchProps<Item extends Record<string, unknown>> = {
  items: Item[];
  keyAttribute?: string;
  itemTitle: (item: Item) => string;
  itemSubTitle?: (item: Item) => string;
  placeholder: string;
  onChange?: (search: string) => void;
  onSelect: (item: Item) => unknown;
  filter?: (item: Item) => boolean;
  autoFocus?: boolean;
  invalid?: boolean;
};

type ItemWithTitleAndSubTitle<Item> = Item & {
  title: string;
  subTitle?: string;
};

const InvalidSearchWrapper = styled.div<{ $invalid: boolean }>`
  ${({ $invalid }) =>
    $invalid &&
    `
    .cds--search-input {
      outline: 2px solid var(--cds-support-error, #da1e28);
      outline-offset: -2px;
    }
  `}
`;

const ListStyleWrapper = styled.div`
  & .cds--list-box__menu-item,
  & .cds--list-box__menu-item__option {
    height: auto;
  }
`;

const MenuItemWrapper = styled.div<{ $isSelected: boolean }>`
  & .cds--list-box__menu-item {
    ${({ $isSelected }) =>
      $isSelected
        ? "background-color: var(--cds-layer-selected) !important"
        : ""};
  }
`;
const DropdownSearch = <Item extends Record<string, unknown>>({
  placeholder,
  onChange = () => {},
  keyAttribute = "title",
  items,
  itemTitle,
  itemSubTitle,
  onSelect,
  filter = () => true,
  autoFocus = false,
  invalid = false,
}: DropdownSearchProps<Item>) => {
  const debounce = useDebounce();
  const [search, setSearch] = useState("");
  const [selectedResult, setSelectedResult] = useState<number>(-1);

  // `items` is already the server's search result for the current `search`
  // text, so it's rendered directly rather than accumulated/re-filtered here.
  const filteredItems: ItemWithTitleAndSubTitle<Item>[] = useMemo(
    () =>
      items.map((item) => ({
        ...item,
        title: itemTitle(item),
        subTitle: itemSubTitle ? itemSubTitle(item) : undefined,
      })),
    [items, itemTitle, itemSubTitle],
  );

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = e.target;
    setSearch(value);
    debounce(() => onChange(value));
    setSelectedResult(-1);
  };

  const handleClear = () => {
    setSearch("");
    onChange("");
    setSelectedResult(-1);
  };

  const handleSelect = (item: Item) => {
    onSelect(item);
    setSearch("");
    onChange("");
    setSelectedResult(-1);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLElement>) => {
    if (
      event.key === "ArrowDown" &&
      selectedResult < filteredItems.length - 1
    ) {
      event.preventDefault();
      event.stopPropagation();
      setSelectedResult(selectedResult + 1);
    }

    if (event.key === "ArrowUp" && selectedResult > 0) {
      event.preventDefault();
      event.stopPropagation();
      setSelectedResult(selectedResult - 1);
    }

    if (
      event.key === "Enter" &&
      selectedResult >= 0 &&
      selectedResult < filteredItems.length
    ) {
      event.preventDefault();
      event.stopPropagation();
      handleSelect(filteredItems[selectedResult]);
    }
  };

  useEffect(() => {
    if (filteredItems.length > 0) setSelectedResult(0);
  }, [filteredItems.length]);

  return (
    <ListBox disabled={false} type="inline" isOpen>
      <InvalidSearchWrapper $invalid={invalid}>
        <Search
          labelText={placeholder}
          placeholder={placeholder}
          onChange={handleChange}
          onClear={handleClear}
          value={search}
          autoFocus={autoFocus}
          onKeyDown={handleKeyDown}
        />
      </InvalidSearchWrapper>
      {search && (
        <ListStyleWrapper>
          <ListBox.Menu id="list-box">
            {filteredItems.filter(filter).map((item, index) => {
              const { title, subTitle } = item;

              return (
                <MenuItemWrapper
                  key={item[keyAttribute] as string}
                  $isSelected={index === selectedResult}
                >
                  <ListBox.MenuItem
                    title={title}
                    onClick={() => handleSelect(item)}
                  >
                    {title}
                    {subTitle && <SecondaryText>{subTitle}</SecondaryText>}
                  </ListBox.MenuItem>
                </MenuItemWrapper>
              );
            })}
          </ListBox.Menu>
        </ListStyleWrapper>
      )}
    </ListBox>
  );
};

export default DropdownSearch;
