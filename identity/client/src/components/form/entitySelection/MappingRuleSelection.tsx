/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import EntitySearchMultiSelect from "src/components/form/EntitySearchMultiSelect";
import EntitySearchSingleSelect from "src/components/form/EntitySearchSingleSelect";
import MappingRuleSearchDropdown from "src/components/form/MappingRuleSearchDropdown";
import type { MappingRule } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (mappingRule: MappingRule) => mappingRule.mappingRuleId;
const itemToString = (mappingRule: MappingRule) =>
  mappingRule.name || mappingRule.mappingRuleId;

type MappingRuleMultiSelectProps = {
  value: MappingRule[];
  onChange: (mappingRules: MappingRule[]) => void;
  excluded?: MappingRule[];
  autoFocus?: boolean;
};

export const MappingRuleMultiSelect: FC<MappingRuleMultiSelectProps> = (
  props,
) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      searchDropdown={MappingRuleSearchDropdown}
      getId={getId}
      placeholder={t("searchByMappingRuleId")}
      errorTitle={t("mappingRulesCouldNotLoad")}
      {...props}
    />
  );
};

type MappingRuleSingleSelectProps = {
  label: string;
  placeholder: string;
  requiredText: string;
  onChange: (mappingRuleId: string) => void;
  value?: string;
  isEmpty?: boolean;
};

export const MappingRuleSingleSelect: FC<MappingRuleSingleSelectProps> = (
  props,
) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      searchDropdown={MappingRuleSearchDropdown}
      getId={getId}
      itemToString={itemToString}
      errorTitle={t("mappingRulesCouldNotLoad")}
      {...props}
    />
  );
};
