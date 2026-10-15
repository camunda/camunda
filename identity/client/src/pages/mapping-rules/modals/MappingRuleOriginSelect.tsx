/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Select, SelectItem } from "@carbon/react";
import useTranslate from "src/utility/localization";
import { isUserProvisioningEnabled, scimSources } from "src/configuration";

type MappingRuleOriginSelectProps = {
  value: string;
  onChange: (value: string) => void;
};

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
    <Select
      id="mapping-rule-origin"
      labelText={t("origin")}
      helperText={t("originHelper")}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      <SelectItem value="" text={t("originAny")} />
      {sources.map((source) => (
        <SelectItem key={source} value={source} text={source} />
      ))}
    </Select>
  );
};

export default MappingRuleOriginSelect;
