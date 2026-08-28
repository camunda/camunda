/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useEffect, useState } from "react";
import { Label } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import {
  EntitySearchDropdown,
  RemovableEntityBadge,
  type EntitySearchQuery,
} from "src/components/formV2/EntitySearchDropdown";

type AbstractEntitySearchSingleSelectProps<
  Entity extends Record<string, unknown>,
> = {
  search: (search: string) => EntitySearchQuery<Entity>;
  itemSubTitle?: (entity: Entity) => string;
  getId: (entity: Entity) => string;
  itemToString: (entity: Entity) => string;
  label: string;
  placeholder: string;
  errorTitle: string;
  requiredText: string;
  onChange: (id: string) => void;
  value?: string;
  isEmpty?: boolean;
};

/**
 * Public props for a concrete per-entity single-select (e.g. UserSingleSelect):
 * everything technical (search, getId, itemToString, errorTitle) is bound by
 * the concrete implementation. itemSubTitle gets a concrete default but
 * remains overridable per call site, since some consumers need different copy.
 */
export type EntitySearchSingleSelectProps<
  Entity extends Record<string, unknown>,
> = Omit<
  AbstractEntitySearchSingleSelectProps<Entity>,
  "search" | "getId" | "itemToString" | "itemSubTitle" | "errorTitle"
> &
  Partial<Pick<AbstractEntitySearchSingleSelectProps<Entity>, "itemSubTitle">>;

const EntitySearchSingleSelect = <Entity extends Record<string, unknown>>({
  search,
  itemSubTitle,
  getId,
  itemToString,
  label,
  placeholder,
  errorTitle,
  requiredText,
  onChange,
  value,
  isEmpty = false,
}: AbstractEntitySearchSingleSelectProps<Entity>) => {
  const { t } = useTranslate();
  const [selectedEntity, setSelectedEntity] = useState<Entity | null>(null);

  useEffect(() => {
    setSelectedEntity((current) =>
      current && value && getId(current) === value ? current : null,
    );
  }, [value, getId]);

  const handleSelect = (entity: Entity) => {
    setSelectedEntity(entity);
    onChange(getId(entity));
  };

  const handleClear = () => {
    setSelectedEntity(null);
    onChange("");
  };

  return (
    <div>
      <Label>{label}</Label>
      {selectedEntity ? (
        <div className="mt-2">
          <RemovableEntityBadge
            label={itemToString(selectedEntity)}
            onRemove={handleClear}
          />
        </div>
      ) : (
        <EntitySearchDropdown
          search={search}
          itemTitle={getId}
          itemSubTitle={itemSubTitle}
          placeholder={placeholder}
          onSelect={handleSelect}
          errorTitle={errorTitle}
          retryLabel={t("retry")}
          invalid={isEmpty}
        />
      )}
      {isEmpty && (
        <p className="mt-1 text-xs text-danger-action-default">
          {requiredText}
        </p>
      )}
    </div>
  );
};

export default EntitySearchSingleSelect;
