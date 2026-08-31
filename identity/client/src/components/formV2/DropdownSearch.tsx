/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useEffect, useMemo, useState } from "react";
import {
  Button,
  cn,
  Command,
  CommandInput,
  CommandItem,
  CommandList,
  Popover,
  PopoverAnchor,
  PopoverContent,
  Text,
} from "@camunda/design-system";
import { XIcon } from "lucide-react";
import useDebounce from "react-debounced";
import useTranslate from "src/utility/localization";

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
  const { t } = useTranslate();
  const debounce = useDebounce();
  const [search, setSearch] = useState("");
  const [highlightedKey, setHighlightedKey] = useState<string>();

  // `items` is already the server's search result for the current `search`
  // text, so it's rendered directly rather than accumulated/re-filtered here.
  const visibleItems: ItemWithTitleAndSubTitle<Item>[] = useMemo(
    () =>
      items
        .map((item) => ({
          ...item,
          title: itemTitle(item),
          subTitle: itemSubTitle ? itemSubTitle(item) : undefined,
        }))
        .filter(filter),
    [items, itemTitle, itemSubTitle, filter],
  );

  useEffect(() => {
    setHighlightedKey(visibleItems[0]?.[keyAttribute] as string | undefined);
  }, [visibleItems, keyAttribute]);

  const handleSearchChange = (value: string) => {
    setSearch(value);
    debounce(() => onChange(value));
  };

  const handleClear = () => {
    setSearch("");
    onChange("");
  };

  const handleSelect = (item: Item) => {
    onSelect(item);
    setSearch("");
    onChange("");
  };

  return (
    <Command
      shouldFilter={false}
      value={highlightedKey}
      onValueChange={setHighlightedKey}
      className="w-full overflow-visible bg-transparent"
    >
      <Popover
        open={Boolean(search)}
        onOpenChange={(open) => {
          if (!open) handleClear();
        }}
      >
        <PopoverAnchor asChild>
          <div
            className={cn(
              "relative",
              invalid && "ring-2 ring-inset ring-danger-action-default",
            )}
          >
            <CommandInput
              value={search}
              onValueChange={handleSearchChange}
              placeholder={placeholder}
              autoFocus={autoFocus}
              aria-label={placeholder}
              aria-invalid={invalid}
              className={cn(search && "pr-8")}
            />
            {search && (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="absolute inset-y-0 right-1 my-auto"
                aria-label={t("clearSearch")}
                onClick={handleClear}
              >
                <XIcon aria-hidden="true" />
              </Button>
            )}
          </div>
        </PopoverAnchor>
        <PopoverContent
          align="start"
          sideOffset={4}
          onOpenAutoFocus={(e) => e.preventDefault()}
          // `CommandInput` lives outside this content (in the anchor), so a
          // mousedown here would otherwise blur it — Radix then reads that
          // blur as focus leaving the popover and dismisses it before the
          // click ever fires, so `CommandItem.onSelect` never runs. Keeping
          // focus on the input the whole time avoids the race entirely.
          onMouseDown={(e) => e.preventDefault()}
          // `pointer-events-auto` restores interactivity inside a Dialog: the
          // popover portals as a sibling of the dialog, so it inherits
          // `pointer-events: none` from the scroll-locked <body> (DS #496).
          className="pointer-events-auto w-(--radix-popover-trigger-width) p-1"
        >
          <CommandList>
            {visibleItems.map((item) => {
              const { title, subTitle } = item;

              return (
                <CommandItem
                  key={item[keyAttribute] as string}
                  value={item[keyAttribute] as string}
                  title={title}
                  onSelect={() => handleSelect(item)}
                  className="flex-col items-start"
                >
                  {title}
                  {subTitle && (
                    <Text as="p" variant="helper">
                      {subTitle}
                    </Text>
                  )}
                </CommandItem>
              );
            })}
          </CommandList>
        </PopoverContent>
      </Popover>
    </Command>
  );
};

export default DropdownSearch;
