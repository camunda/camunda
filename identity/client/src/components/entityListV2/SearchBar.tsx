/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { SearchInput } from "@camunda/design-system";
import { FC, useEffect, useRef, useState } from "react";
import useDebounce from "react-debounced";
import useTranslate from "src/utility/localization";
import { SearchFilterValue } from "src/utility/api/hooks/usePagination";

type SearchBarProps = {
  searchKey: string;
  onSearch: (value: Record<string, SearchFilterValue> | undefined) => void;
  searchPlaceholder?: string;
  debounce?: number;
  /** "eq" (default) matches the typed value exactly; "like" matches it as a substring. */
  searchOperator?: "eq" | "like";
};

/** Escapes the backend's LIKE wildcard characters (`*`, `?`) and their escape prefix (`\`) so a typed value matches literally. */
function escapeWildcards(value: string): string {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/\*/g, "\\*")
    .replace(/\?/g, "\\?");
}

const NOT_EMITTED = "";

export default function SearchBar({
  searchPlaceholder,
  searchKey,
  onSearch,
  debounce = 300,
  searchOperator = "eq",
}: SearchBarProps): ReturnType<FC> {
  const { t } = useTranslate("components");
  const [search, setSearchState] = useState<string>("");
  const debounceFn = useDebounce(debounce);
  const emittedSearch = useRef(NOT_EMITTED);

  useEffect(() => {
    if (!searchKey) {
      return;
    }

    const emit = (value: Record<string, SearchFilterValue> | undefined) => {
      const serialized = JSON.stringify(value) ?? "undefined";

      if (serialized !== emittedSearch.current) {
        emittedSearch.current = serialized;
        onSearch(value);
      }
    };

    if (!search || search.trim().length === 0) {
      debounceFn(() => emit(undefined));
      return;
    }

    const value: SearchFilterValue =
      searchOperator === "like"
        ? { $like: `*${escapeWildcards(search)}*` }
        : search;
    debounceFn(() => emit({ [searchKey]: value }));
  }, [debounceFn, onSearch, search, searchKey, searchOperator]);

  return (
    <SearchInput
      className="flex-1"
      placeholder={searchPlaceholder}
      aria-label={searchPlaceholder ?? t("search")}
      clearLabel={t("clearSearch")}
      value={search}
      onChange={(event) => {
        setSearchState(event.target.value);
      }}
      onClear={() => {
        setSearchState("");
      }}
    />
  );
}
