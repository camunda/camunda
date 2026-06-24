/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Dropdown as CarbonDropdown } from "@carbon/react";
import { DropdownProps } from "@carbon/react/lib/components/Dropdown/Dropdown";
import { spaceAndCapitalize } from "src/utility/format/spaceAndCapitalize.ts";
import useTranslate from "src/utility/localization";

const ALL_OPTION = "all" as const;

type Props<T extends string> = Omit<
  DropdownProps<T | typeof ALL_OPTION>,
  "label" | "items" | "selectedItem" | "onChange" | "itemToString"
> & {
  items: readonly T[];
  selectedItem?: T;
  onChange?: (data: { selectedItem?: T }) => void;
};

const Select = <T extends string>({
  onChange,
  items,
  selectedItem,
  ...rest
}: Props<T>) => {
  const { t } = useTranslate("components");
  const options: (T | typeof ALL_OPTION)[] = [ALL_OPTION, ...items];

  const controlledSelectedItem = selectedItem ?? ALL_OPTION;

  function onDropdownChange({
    selectedItem,
  }: {
    selectedItem: T | typeof ALL_OPTION | null;
  }) {
    if (!selectedItem || selectedItem === ALL_OPTION) {
      onChange?.({ selectedItem: undefined });
      return;
    }

    onChange?.({ selectedItem });
  }

  return (
    <CarbonDropdown<T | typeof ALL_OPTION>
      {...rest}
      label={t("selectLabel")}
      aria-label={t("selectLabel")}
      size="sm"
      items={options}
      selectedItem={controlledSelectedItem}
      itemToString={(item) =>
        item === ALL_OPTION
          ? t("selectAll")
          : item
            ? spaceAndCapitalize(item)
            : ""
      }
      onChange={onDropdownChange}
    />
  );
};

export { Select };
