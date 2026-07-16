/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { groupQueries } from "src/utility/api/groups/queries";
import DropdownSearch from "src/components/form/DropdownSearch";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

type GroupSearchDropdownProps = {
  onSelect: (group: Group) => void;
  filter?: (group: Group) => boolean;
  placeholder: string;
  autoFocus?: boolean;
  errorTitle: string;
  retryLabel: string;
};

// Searches and fetches groups on its own, so callers only need to render it
// and react to the selection.
const GroupSearchDropdown: FC<GroupSearchDropdownProps> = ({
  onSelect,
  filter,
  placeholder,
  autoFocus = false,
  errorTitle,
  retryLabel,
}) => {
  const [search, setSearch] = useState("");

  const {
    data: groupSearchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useQuery(
    groupQueries.search(
      search === "" ? {} : { filter: { groupId: { $like: `*${search}*` } } },
    ),
  );

  return (
    <>
      <DropdownSearch
        autoFocus={autoFocus}
        items={groupSearchResults?.items || []}
        itemTitle={({ groupId }) => groupId}
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

export default GroupSearchDropdown;
