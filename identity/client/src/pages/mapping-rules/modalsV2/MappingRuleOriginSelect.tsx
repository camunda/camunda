/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import FormField from "src/components/formV2/FormField";
import useTranslate from "src/utility/localization";
import { isUserProvisioningEnabled, scimSources } from "src/configuration";

type MappingRuleOriginSelectProps = {
  value: string;
  onChange: (value: string) => void;
};

// Radix-style selects reject an empty item value, so "any source" uses a sentinel.
const ANY_SOURCE = "__any__";

const MappingRuleOriginSelect: FC<MappingRuleOriginSelectProps> = ({
  value,
  onChange,
}) => {
  const { t } = useTranslate("mappingRules");

  if (!isUserProvisioningEnabled) return null;

  // Keep a stored origin selectable even if its source is no longer configured.
  const sources =
    value && !scimSources.includes(value)
      ? [...scimSources, value]
      : scimSources;

  return (
    <FormField label={t("origin")} helperText={t("originHelper")}>
      {(control) => (
        <Select
          value={value || ANY_SOURCE}
          onValueChange={(next) => onChange(next === ANY_SOURCE ? "" : next)}
        >
          <SelectTrigger className="w-full" {...control}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ANY_SOURCE}>{t("originAny")}</SelectItem>
            {sources.map((source) => (
              <SelectItem key={source} value={source}>
                {source}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    </FormField>
  );
};

export default MappingRuleOriginSelect;
