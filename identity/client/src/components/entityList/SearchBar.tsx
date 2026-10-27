/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { TableToolbarSearch } from "@carbon/react";
import { FC, useEffect, useState } from "react";

import useDebounce from "react-debounced";

type SearchBarProps = {
  searchKey: string;
  onSearch: (value: Record<string, string> | undefined) => void;
  searchPlaceholder?: string;
  debounce?: number;
};

export default function SearchBar({
  searchPlaceholder,
  searchKey,
  onSearch,
  debounce = 300,
}: SearchBarProps): ReturnType<FC> {
  const [search, setSearchState] = useState<string>("");
  const debounceFn = useDebounce(debounce);

  useEffect(() => {
    if (!searchKey) {
      return;
    }

    if (!search || search.trim().length === 0) {
      debounceFn(() => onSearch(undefined));
      return;
    }

    debounceFn(() => onSearch({ [searchKey]: search }));
  }, [debounceFn, onSearch, search, searchKey]);

  return (
    <TableToolbarSearch
      placeholder={searchPlaceholder}
      value={search}
      persistent
      onChange={(_, value = "") => {
        setSearchState(value);
      }}
      onFocus={(event, handleExpand) => {
        handleExpand(event, true);
      }}
      onBlur={(event, handleExpand) => {
        const { value } = event.target;
        if (!value) {
          handleExpand(event, false);
        }
      }}
    />
  );
}
