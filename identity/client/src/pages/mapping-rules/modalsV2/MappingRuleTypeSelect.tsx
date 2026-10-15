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
    <FormField
      label={t("type")}
      helperText={
        value === "USER_ATTRIBUTE"
          ? t("typeUserAttributeHelper")
          : value === "GROUP_ATTRIBUTE"
            ? t("typeGroupAttributeHelper")
            : t("typeTokenClaimHelper")
      }
    >
      {(control) => (
        <Select
          value={value}
          onValueChange={(next) => onChange(next as MappingRuleType)}
        >
          <SelectTrigger className="w-full" {...control}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="TOKEN_CLAIM">{t("typeTokenClaim")}</SelectItem>
            <SelectItem value="USER_ATTRIBUTE">
              {t("typeUserAttribute")}
            </SelectItem>
            <SelectItem value="GROUP_ATTRIBUTE">
              {t("typeGroupAttribute")}
            </SelectItem>
          </SelectContent>
        </Select>
      )}
    </FormField>
  );
};

export default MappingRuleTypeSelect;
