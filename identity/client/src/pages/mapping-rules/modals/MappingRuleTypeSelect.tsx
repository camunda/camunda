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
import type { MappingRuleType } from "src/utility/api/mapping-rules";

type MappingRuleTypeSelectProps = {
  value: MappingRuleType;
  onChange: (value: MappingRuleType) => void;
};

const MappingRuleTypeSelect: FC<MappingRuleTypeSelectProps> = ({
  value,
  onChange,
}) => {
  const { t } = useTranslate("mappingRules");

  return (
    <Select
      id="mapping-rule-type"
      labelText={t("type")}
      helperText={
        value === "USER_ATTRIBUTE"
          ? t("typeUserAttributeHelper")
          : value === "GROUP_ATTRIBUTE"
            ? t("typeGroupAttributeHelper")
            : t("typeTokenClaimHelper")
      }
      value={value}
      onChange={(e) => onChange(e.target.value as MappingRuleType)}
    >
      <SelectItem value="TOKEN_CLAIM" text={t("typeTokenClaim")} />
      <SelectItem value="USER_ATTRIBUTE" text={t("typeUserAttribute")} />
      <SelectItem value="GROUP_ATTRIBUTE" text={t("typeGroupAttribute")} />
    </Select>
  );
};

export default MappingRuleTypeSelect;
