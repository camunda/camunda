/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { mappingRuleQueries } from "src/utility/api/mapping-rules/queries";
import DropdownSearch from "src/components/form/DropdownSearch";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import type { EntitySearchDropdown } from "src/components/form/EntitySearchDropdown";
import type { MappingRule } from "@camunda/camunda-api-zod-schemas/8.10";

const MappingRuleSearchDropdown: EntitySearchDropdown<MappingRule> = ({
  onSelect,
  filter,
  placeholder,
  autoFocus = false,
  errorTitle,
  retryLabel,
}) => {
  const [search, setSearch] = useState("");

  const {
    data: mappingRuleSearchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useQuery(
    mappingRuleQueries.search(
      search.trim() ? { filter: { name: search } } : {},
    ),
  );

  return (
    <>
      <DropdownSearch
        autoFocus={autoFocus}
        items={mappingRuleSearchResults?.items || []}
        itemTitle={({ mappingRuleId }) => mappingRuleId}
        itemSubTitle={({ name }) => name}
        placeholder={placeholder}
        onSelect={onSelect}
        onChange={setSearch}
        filter={filter}
      />
      {!loading && error && (
        <TranslatedErrorInlineNotification
          title={errorTitle}
          actionButton={{
            label: retryLabel,
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </>
  );
};

export default MappingRuleSearchDropdown;
