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

type DropdownSearchProps<Item> = {
  items: Item[];
  itemTitle: (item: Item) => string;
  itemSubTitle?: (item: Item) => string;
  placeholder: string;
  onChange?: (search: string) => unknown;
  onSelect: (item: Item) => unknown;
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
const DropdownSearch = <Item,>({
  placeholder,
  onChange = () => null,
  items: rawItems,
  itemTitle,
  itemSubTitle,
  onSelect,
  autoFocus = false,
}: DropdownSearchProps<Item>) => {
  const debounce = useDebounce();
  const [search, setSearch] = useState("");
  const [selectedResult, setSelectedResult] = useState<number>(-1);
  const [filteredItems, setFilteredItems] = useState<
    ItemWithTitleAndSubTitle<Item>[]
  >([]);

  const items = useMemo(() => {
    return rawItems.map((item) => ({
      ...item,
      title: itemTitle(item),
      subTitle: itemSubTitle ? itemSubTitle(item) : undefined,
    }));
  }, [rawItems, itemTitle, itemSubTitle]);

  const fuse = useMemo(
    () =>
      new Fuse(items, {
        keys: ["title", "subTitle"],
        threshold: 0.3,
      }),
    [items],
  );

  useEffect(() => {
    if (search) {
      setFilteredItems(fuse.search(search).map((result) => result.item));
    } else {
      setFilteredItems(items);
    }
  }, [search, items, fuse]);

  const wrapperRef = React.useRef<HTMLDivElement>(null);
  const [hasFocus, setHasFocus] = useState(false);

  const handleWrapperFocus = () => setHasFocus(true);
  const handleWrapperBlur = (e: React.FocusEvent<HTMLDivElement>) => {
    if (
      wrapperRef.current &&
      e.relatedTarget &&
      wrapperRef.current.contains(e.relatedTarget as Node)
    ) {
      // Focus is still within the wrapper
      return;
    }
    setHasFocus(false);
  };

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
    <div
      ref={wrapperRef}
      tabIndex={-1}
      onFocus={handleWrapperFocus}
      onBlur={handleWrapperBlur}
    >
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
        {hasFocus && (
          <ListStyleWrapper>
            <ListBox.Menu id="list-box">
              {filteredItems.map((item, index) => {
                const { title, subTitle } = item;

                return (
                  <MenuItemWrapper
                    key={title}
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
    </div>
  );
};

export default DropdownSearch;
