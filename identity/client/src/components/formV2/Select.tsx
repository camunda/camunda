/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ReactNode, useId } from "react";
import {
  Label,
  Select as DSSelect,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  cn,
} from "@camunda/design-system";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize.ts";
import useTranslate from "src/utility/localization";

const ALL_OPTION = "all" as const;

type Props<T extends string> = {
  id?: string;
  className?: string;
  disabled?: boolean;
  titleText?: ReactNode;
  items: readonly T[];
  selectedItem?: T;
  onChange?: (data: { selectedItem?: T }) => void;
};

const Select = <T extends string>({
  id,
  className,
  disabled,
  titleText,
  items,
  selectedItem,
  onChange,
}: Props<T>) => {
  const { t } = useTranslate("components");
  const generatedId = useId();
  const selectId = id ?? generatedId;

  const controlledSelectedItem = selectedItem ?? ALL_OPTION;

  function handleValueChange(value: string) {
    if (value === ALL_OPTION) {
      onChange?.({ selectedItem: undefined });
      return;
    }

    onChange?.({ selectedItem: value as T });
  }

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      {titleText !== undefined ? (
        <Label htmlFor={selectId}>{titleText}</Label>
      ) : null}
      <DSSelect
        value={controlledSelectedItem}
        onValueChange={handleValueChange}
        disabled={disabled}
      >
        <SelectTrigger
          id={selectId}
          className="w-full"
          aria-label={titleText === undefined ? t("selectLabel") : undefined}
        >
          <SelectValue placeholder={t("selectLabel")} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL_OPTION}>{t("selectAll")}</SelectItem>
          {items.map((item) => (
            <SelectItem key={item} value={item}>
              {spaceAndCapitalize(item)}
            </SelectItem>
          ))}
        </SelectContent>
      </DSSelect>
    </div>
  );
};

export { Select };
