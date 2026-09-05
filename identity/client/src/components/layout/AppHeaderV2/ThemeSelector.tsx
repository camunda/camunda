/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { observer } from "mobx-react-lite";
import {
  Label,
  RadioGroup,
  RadioGroupItem,
  Text,
} from "@camunda/design-system";

import { isThemeOption, themeStore } from "src/common/theme/theme";
import useTranslate from "src/utility/localization";

export const ThemeSelector = observer(() => {
  const { t } = useTranslate("navigation");
  const themeOptions = [
    { value: "light", label: t("themeLight") },
    { value: "system", label: t("themeSystem") },
    { value: "dark", label: t("themeDark") },
  ];

  return (
    <div className="px-2 py-1.5">
      <Text
        as="span"
        variant="label-sm"
        className="text-neutral-foreground-subtle"
      >
        {t("themeSelectorLegend")}
      </Text>
      <RadioGroup
        aria-label={t("themeSelectorLegend")}
        value={themeStore.selectedTheme}
        onValueChange={(value) => {
          if (isThemeOption(value)) {
            themeStore.changeTheme(value);
          }
        }}
        className="mt-2 gap-2"
      >
        {themeOptions.map((option) => (
          <Label key={option.value} className="flex items-center gap-2">
            <RadioGroupItem value={option.value} />
            {option.label}
          </Label>
        ))}
      </RadioGroup>
    </div>
  );
});
