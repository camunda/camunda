/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useId, useMemo } from "react";
import { Combobox, Label, Text } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { TranslatedErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import {
  useEntitySearchQuery,
  type EntitySearchQuery,
} from "src/components/formV2/useEntitySearchQuery";

type AbstractEntitySearchSingleSelectProps<
  Entity extends Record<string, unknown>,
> = {
  search: (search: string) => EntitySearchQuery<Entity>;
  itemLabel: (entity: Entity) => string;
  getId: (entity: Entity) => string;
  label: string;
  placeholder: string;
  errorTitle: string;
  requiredText: string;
  onChange: (id: string) => void;
  value?: string;
  isEmpty?: boolean;
  autoFocus?: boolean;
};

/**
 * Public props for a concrete per-entity single-select (e.g. UserSingleSelect):
 * everything technical (search, getId, itemLabel, errorTitle) is fixed by
 * the concrete implementation and not overridable per call site.
 */
export type EntitySearchSingleSelectProps<
  Entity extends Record<string, unknown>,
> = Omit<
  AbstractEntitySearchSingleSelectProps<Entity>,
  "search" | "getId" | "itemLabel" | "errorTitle"
>;

const EntitySearchSingleSelect = <Entity extends Record<string, unknown>>({
  search,
  itemLabel,
  getId,
  label,
  placeholder,
  errorTitle,
  requiredText,
  onChange,
  value,
  isEmpty = false,
  autoFocus = false,
}: AbstractEntitySearchSingleSelectProps<Entity>) => {
  const { t } = useTranslate();
  const id = useId();
  const { items, isLoading, error, reload, onInputChange } =
    useEntitySearchQuery(search);

  const options = useMemo(
    () =>
      items.map((entity) => ({
        label: itemLabel(entity),
        value: getId(entity),
      })),
    [items, itemLabel, getId],
  );

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Combobox
        id={id}
        className="w-full"
        options={options}
        value={value}
        onValueChange={(newValue) => onChange(newValue ?? "")}
        onInputChange={onInputChange}
        externalFiltering
        placeholder={placeholder}
        autoFocus={autoFocus}
        aria-invalid={isEmpty}
      />
      {isEmpty && (
        <Text
          as="span"
          variant="helper"
          className=" text-danger-foreground-subtle"
        >
          {requiredText}
        </Text>
      )}
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

export default EntitySearchSingleSelect;
