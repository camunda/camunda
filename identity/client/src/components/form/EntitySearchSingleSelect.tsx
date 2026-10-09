/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useEffect, useState } from "react";
import { FormLabel, Tag } from "@carbon/react";
import useTranslate from "src/utility/localization";
import EntitySearchDropdown, {
  type EntitySearchQuery,
} from "src/components/form/EntitySearchDropdown";

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
 * everything technical (search, getId, itemToString, itemSubTitle, errorTitle)
 * is fixed by the concrete implementation and not overridable per call site.
 */
export type EntitySearchSingleSelectProps<
  Entity extends Record<string, unknown>,
> = Omit<
  AbstractEntitySearchSingleSelectProps<Entity>,
  "search" | "getId" | "itemToString" | "itemSubTitle" | "errorTitle"
>;

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

  // Only clears selectedEntity when value no longer matches it; it cannot
  // resync to a new non-empty value set externally (e.g. a pre-filled owner
  // in an edit flow), since there is no way to look up an Entity from just
  // an id here. Safe today because every consumer is a create-only form
  // where value only ever changes via this component's own onChange. If a
  // future caller pre-fills value, pass the full Entity too so it can be
  // set as the initial selectedEntity.
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
      <FormLabel>{label}</FormLabel>
      {selectedEntity ? (
        <div style={{ marginTop: "0.5rem" }}>
          <Tag filter onClose={handleClear} type="blue" size="md">
            {itemToString(selectedEntity)}
          </Tag>
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
        <p
          style={{
            color: "var(--cds-text-error, #da1e28)",
            fontSize: "0.75rem",
            marginTop: "0.25rem",
          }}
        >
          {requiredText}
        </p>
      )}
    </div>
  );
};

export default EntitySearchSingleSelect;
