/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Badge } from "@camunda/design-system";
import { XIcon } from "lucide-react";
import DropdownSearch from "src/components/formV2/DropdownSearch";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";

type RemovableEntityBadgeProps = {
  label: string;
  onRemove: () => void;
};

// Mirrors the DS's own MultiSelect chip styling (`Badge` + inline remove
// button), which isn't exposed (as of v0.49.0) and MultiSelect cannot be used with lazy server-side search.
export const RemovableEntityBadge = ({
  label,
  onRemove,
}: RemovableEntityBadgeProps) => (
  <Badge variant="neutral" className="gap-0.5 pr-1.5">
    <span>{label}</span>
    <button
      type="button"
      aria-label={`Remove ${label}`}
      onClick={onRemove}
      className="ml-0.5 p-0 shrink-0 rounded-sm opacity-60 hover:opacity-100 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
    >
      <XIcon className="size-3" aria-hidden="true" />
    </button>
  </Badge>
);

export type EntitySearchQuery<Entity> = {
  queryKey: readonly unknown[];
  // The generated query builders type their queryFn's context param more
  // narrowly per entity; `any` here just needs to accept whatever they pass.
  queryFn?: (
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    context: any,
  ) => Promise<{ items: Entity[] }> | { items: Entity[] };
};

type EntitySearchDropdownProps<Entity extends Record<string, unknown>> = {
  search: (search: string) => EntitySearchQuery<Entity>;
  itemTitle: (entity: Entity) => string;
  itemSubTitle?: (entity: Entity) => string;
  onSelect: (entity: Entity) => void;
  filter?: (entity: Entity) => boolean;
  placeholder: string;
  autoFocus?: boolean;
  invalid?: boolean;
  errorTitle: string;
  retryLabel: string;
};

/**
 * Generic search dropdown for any entity: fetches items via the given
 * search query as the user types, and renders them through DropdownSearch.
 */
export const EntitySearchDropdown = <Entity extends Record<string, unknown>>({
  search: buildSearchQuery,
  itemTitle,
  itemSubTitle,
  onSelect,
  filter,
  placeholder,
  autoFocus = false,
  invalid = false,
  errorTitle,
  retryLabel,
}: EntitySearchDropdownProps<Entity>) => {
  const [search, setSearch] = useState("");

  const {
    data: searchResults,
    isLoading: loading,
    refetch: reload,
    error,
  } = useQuery<{ items: Entity[] }>({
    ...buildSearchQuery(search),
    enabled: search !== "",
  });

  return (
    <>
      <DropdownSearch
        autoFocus={autoFocus}
        items={searchResults?.items || []}
        itemTitle={itemTitle}
        itemSubTitle={itemSubTitle}
        placeholder={placeholder}
        onSelect={onSelect}
        onChange={setSearch}
        filter={filter}
        invalid={invalid}
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
