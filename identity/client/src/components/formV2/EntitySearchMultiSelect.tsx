/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useMemo } from "react";
import { MultiSelect } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import {
  useEntitySearchQuery,
  type EntitySearchQuery,
} from "src/components/formV2/useEntitySearchQuery";

type AbstractEntitySearchMultiSelectProps<
  Entity extends Record<string, unknown>,
> = {
  search: (search: string) => EntitySearchQuery<Entity>;
  itemLabel: (entity: Entity) => string;
  getId: (entity: Entity) => string;
  value: Entity[];
  onChange: (entities: Entity[]) => void;
  excluded?: Entity[];
  placeholder: string;
  errorTitle: string;
};

/**
 * Public props for a concrete per-entity multi-select (e.g. UserMultiSelect):
 * everything technical (search, getId, itemLabel, errorTitle) is fixed by
 * the concrete implementation and not overridable per call site. placeholder
 * gets a concrete default but remains overridable per call site, since some
 * consumers need different copy.
 */
export type EntitySearchMultiSelectProps<
  Entity extends Record<string, unknown>,
> = Omit<
  AbstractEntitySearchMultiSelectProps<Entity>,
  "search" | "getId" | "itemLabel" | "placeholder" | "errorTitle"
> &
  Partial<Pick<AbstractEntitySearchMultiSelectProps<Entity>, "placeholder">>;

// MultiSelect's overflow "+N more" chip removes every selection past
// `maxCount` when cleared (it slices `selected` down to `maxCount`), which
// would silently drop assignments beyond the cap. Keep every chip visible.
const NO_LIMIT = Infinity;

const EntitySearchMultiSelect = <Entity extends Record<string, unknown>>({
  search,
  itemLabel,
  getId,
  value,
  onChange,
  excluded = [],
  placeholder,
  errorTitle,
}: AbstractEntitySearchMultiSelectProps<Entity>) => {
  const { t } = useTranslate();
  const {
    items,
    search: searchText,
    isLoading,
    error,
    reload,
    onInputChange,
  } = useEntitySearchQuery(search);

  // `MultiSelect` only deals in ids, but callers need the full selected
  // `Entity` objects back. `externalFiltering` also means `items` is only the
  // current query's result page, so a value selected on a prior query can
  // drop off `items` entirely. This keeps selections resolvable by id.
  const entityById = useMemo(() => {
    const map = new Map<string, Entity>();
    for (const entity of items) map.set(getId(entity), entity);
    for (const entity of value) map.set(getId(entity), entity);
    return map;
  }, [items, value, getId]);

  const excludedIds = useMemo(
    () => new Set(excluded.map(getId)),
    [excluded, getId],
  );

  const options = useMemo(
    () =>
      (searchText === "" ? value : items)
        .filter((entity) => !excludedIds.has(getId(entity)))
        .map((entity) => ({ label: itemLabel(entity), value: getId(entity) })),
    [searchText, items, value, excludedIds, getId, itemLabel],
  );

  const handleValueChange = useCallback(
    (ids: string[]) => {
      onChange(
        ids
          .map((id) => entityById.get(id))
          .filter((entity): entity is Entity => entity !== undefined),
      );
    },
    [onChange, entityById],
  );

  return (
    <div>
      <MultiSelect
        options={options}
        value={value.map(getId)}
        onValueChange={handleValueChange}
        onInputChange={onInputChange}
        externalFiltering
        maxCount={NO_LIMIT}
        placeholder={placeholder}
        searchPlaceholder={placeholder}
        emptyIndicator={isLoading ? t("loading") : undefined}
        aria-label={placeholder}
      />
      {!isLoading && error && (
        <TranslatedErrorInlineNotification
          title={errorTitle}
          actionButton={{
            label: t("retry"),
            onClick: () => {
              void reload();
            },
          }}
        />
      )}
    </div>
  );
};

export default EntitySearchMultiSelect;
