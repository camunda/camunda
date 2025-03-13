import React, { ChangeEvent, useEffect, useState } from "react";
import { Search } from "@carbon/react";
import ListBox from "@carbon/react/lib/components/ListBox";
import useDebounce from "react-debounced";
import { SecondaryText } from "src/components/form/Text";
import styled from "styled-components";

type DropdownSearchProps<Item> = {
  items: Item[];
  itemTitle: (item: Item) => string;
  itemSubTitle?: (item: Item) => string;
  placeholder: string;
  onChange?: (search: string) => unknown;
  onSelect: (item: Item) => unknown;
  autoFocus?: boolean;
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
  items,
  itemTitle,
  itemSubTitle,
  onSelect,
  autoFocus = false,
}: DropdownSearchProps<Item>) => {
  const debounce = useDebounce();
  const [search, setSearch] = useState("");
  const [selectedResult, setSelectedResult] = useState<number>(-1);

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
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
    if (event.key === "ArrowDown" && selectedResult < items.length - 1) {
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
      selectedResult < items.length
    ) {
      event.preventDefault();
      event.stopPropagation();
      handleSelect(items[selectedResult]);
    }
  };

  useEffect(() => {
    if (items.length > 0) setSelectedResult(0);
  }, [items.length]);

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
            {items.map((item, index) => {
              const title = itemTitle(item);

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
                    {itemSubTitle && (
                      <SecondaryText>{itemSubTitle(item)}</SecondaryText>
                    )}
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
