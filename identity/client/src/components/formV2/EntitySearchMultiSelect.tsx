/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback } from "react";
import useTranslate from "src/utility/localization";
import {
  EntitySearchDropdown,
  RemovableEntityBadge,
  type EntitySearchQuery,
} from "src/components/formV2/EntitySearchDropdown";

type AbstractEntitySearchMultiSelectProps<
  Entity extends Record<string, unknown>,
> = {
  search: (search: string) => EntitySearchQuery<Entity>;
  itemSubTitle?: (entity: Entity) => string;
  getId: (entity: Entity) => string;
  value: Entity[];
  onChange: (entities: Entity[]) => void;
  excluded?: Entity[];
  placeholder: string;
  errorTitle: string;
  autoFocus?: boolean;
};

/**
 * Public props for a concrete per-entity multi-select (e.g. UserMultiSelect):
 * everything technical (search, getId, errorTitle) is bound by the concrete
 * implementation. itemSubTitle/placeholder get concrete defaults but remain
 * overridable per call site, since some consumers need different copy.
 */
export type EntitySearchMultiSelectProps<
  Entity extends Record<string, unknown>,
> = Omit<
  AbstractEntitySearchMultiSelectProps<Entity>,
  "search" | "getId" | "itemSubTitle" | "placeholder" | "errorTitle"
> &
  Partial<
    Pick<
      AbstractEntitySearchMultiSelectProps<Entity>,
      "itemSubTitle" | "placeholder"
    >
  >;

const EntitySearchMultiSelect = <Entity extends Record<string, unknown>>({
  search,
  itemSubTitle,
  getId,
  value,
  onChange,
  excluded = [],
  placeholder,
  errorTitle,
  autoFocus = false,
}: AbstractEntitySearchMultiSelectProps<Entity>) => {
  const { t } = useTranslate();

  const isAlreadyPicked = useCallback(
    (entity: Entity) =>
      excluded.some((picked) => getId(picked) === getId(entity)) ||
      value.some((picked) => getId(picked) === getId(entity)),
    [excluded, value, getId],
  );

  const handleSelect = (entity: Entity) => {
    onChange([...value, entity]);
  };

  const handleUnselect = (entity: Entity) => () => {
    onChange(value.filter((picked) => getId(picked) !== getId(entity)));
  };

  return (
    <div className="flex flex-col gap-2">
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {value.map((entity) => {
            const label = getId(entity);
            return (
              <RemovableEntityBadge
                key={label}
                label={label}
                onRemove={handleUnselect(entity)}
              />
            );
          })}
        </div>
      )}
      <EntitySearchDropdown
        search={search}
        itemTitle={getId}
        itemSubTitle={itemSubTitle}
        autoFocus={autoFocus}
        placeholder={placeholder}
        onSelect={handleSelect}
        filter={(entity) => !isAlreadyPicked(entity)}
        errorTitle={errorTitle}
        retryLabel={t("retry")}
      />
    </div>
  );
};

export default EntitySearchMultiSelect;
