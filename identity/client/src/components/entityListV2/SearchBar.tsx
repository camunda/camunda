/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { SearchInput } from "@camunda/design-system";
import { FC, useEffect, useState } from "react";
import useDebounce from "react-debounced";
import useTranslate from "src/utility/localization";

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
  const { t } = useTranslate("components");
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
