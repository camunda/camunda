/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, { useEffect, useMemo, useState } from "react";
import { Search } from "@carbon/react";
import ListBox from "@carbon/react/lib/components/ListBox";
import useDebounce from "react-debounced";
import { SecondaryText } from "src/components/form/Text";
import styled from "styled-components";
import Fuse from "fuse.js";

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
};

type ItemWithTitleAndSubTitle<Item> = Item & {
  title: string;
  subTitle?: string;
};

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
  items: rawItems,
  itemTitle,
  itemSubTitle,
  onSelect,
  filter = () => true, // We require a filter to allow the parent to remove items with accumulated state. See comment below for details.
  autoFocus = false,
}: DropdownSearchProps<Item>) => {
  const debounce = useDebounce();
  const [search, setSearch] = useState("");
  const [selectedResult, setSelectedResult] = useState<number>(-1);
  const [filteredItems, setFilteredItems] = useState<
    ItemWithTitleAndSubTitle<Item>[]
  >([]);

  const [allItems, setAllItems] = useState<ItemWithTitleAndSubTitle<Item>[]>(
    [],
  );

  // We are accumulating all itemsm even if the `items` prop changes.
  // This allows the parent component to pass new items into the dropdown, e.g.
  // when the user types in the search field.
  //
  // === THIS IS A WORKAROUND ===
  // Ideally, we can use the response from the server directly and don't need to
  // keep state here. However, as some endpoints only allow for exact matches,
  // this would invalidate the use case of a dropdown search.
  // We should refactor this component to use the server response directly.
  //
  // Related to https://github.com/camunda/camunda/issues/36493
  useEffect(() => {
    setAllItems((prevItems) => {
      const allKeys = prevItems.map((item) => item[keyAttribute] as string);

      const newItems = rawItems
        .map((item) => ({
          ...item,
          title: itemTitle(item),
          subTitle: itemSubTitle ? itemSubTitle(item) : undefined,
        }))
        .filter((item) => !allKeys.includes(item[keyAttribute] as string));
      return [...prevItems, ...newItems];
    });
  }, [rawItems, itemTitle, itemSubTitle, keyAttribute]);

  const fuse = useMemo(
    () =>
      new Fuse(allItems, {
        keys: ["title", "subTitle"],
        threshold: 0.3,
      }),
    [allItems],
  );

  useEffect(() => {
    if (search) {
      setFilteredItems(fuse.search(search).map((result) => result.item));
    } else {
      setFilteredItems(allItems);
    }
  }, [search, allItems, fuse]);

  const handleChange = (e: { target: HTMLInputElement; type: "change" }) => {
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
      <Search
        labelText={placeholder}
        placeholder={placeholder}
        onChange={handleChange}
        onClear={handleClear}
        value={search}
        autoFocus={autoFocus}
        onKeyDown={handleKeyDown}
      />
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
