/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ComboBox, TextInputSkeleton } from "@carbon/react";
import { SearchResponse, useApi } from "src/utility/api";
import { ApiDefinition } from "src/utility/api/request";
import useTranslate from "src/utility/localization";

type OwnerSelectionProps<T> = {
  id: string;
  onChange: (OwnerId: string) => void;
  onBlur: () => void;
  searchFn: ApiDefinition<SearchResponse<T>>;
  getId: (item: T) => string;
  itemToString: (item: T) => string;
  isEmpty?: boolean;
};

const OwnerSelection = <T,>({
  id,
  onChange,
  onBlur,
  searchFn,
  getId,
  itemToString,
  isEmpty = false,
}: OwnerSelectionProps<T>) => {
  const { t } = useTranslate("authorizations");
  const { data, loading } = useApi(searchFn);

  if (loading) {
    return <TextInputSkeleton />;
  }

  return (
    Array.isArray(data?.items) && (
      <ComboBox
        id={id}
        key={id}
        titleText={t("owner")}
        items={data.items}
        placeholder={t("selectOwner")}
        onChange={({ selectedItem }) => {
          if (selectedItem) {
            onChange(getId(selectedItem));
          }
        }}
        onBlur={onBlur}
        itemToString={(item) => (item ? itemToString(item) : "")}
        shouldFilterItem={({ inputValue, item }) => {
          if (item && inputValue) {
            const value = itemToString(item).toLowerCase();
            return value.includes(inputValue.toLowerCase());
          }
          return true;
        }}
        invalid={isEmpty}
        invalidText={t("ownerRequired")}
      />
    )
  );
};

export default OwnerSelection;
