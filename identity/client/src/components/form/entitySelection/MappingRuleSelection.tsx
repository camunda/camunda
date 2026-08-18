/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import EntitySearchMultiSelect, {
  type EntitySearchMultiSelectProps,
} from "src/components/form/EntitySearchMultiSelect";
import EntitySearchSingleSelect, {
  type EntitySearchSingleSelectProps,
} from "src/components/form/EntitySearchSingleSelect";
import { mappingRuleQueries } from "src/utility/api/mapping-rules/queries";
import type { MappingRule } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (mappingRule: MappingRule) => mappingRule.mappingRuleId;
const itemToString = (mappingRule: MappingRule) =>
  mappingRule.name || mappingRule.mappingRuleId;
const itemSubTitle = (mappingRule: MappingRule) => mappingRule.name;
const search = (search: string) =>
  mappingRuleQueries.search(
    search.trim() ? { filter: { name: { $like: `*${search}*` } } } : {},
  );

export const MappingRuleMultiSelect: FC<
  EntitySearchMultiSelectProps<MappingRule>
> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      search={search}
      getId={getId}
      itemSubTitle={itemSubTitle}
      placeholder={t("searchByMappingRuleId")}
      errorTitle={t("mappingRulesCouldNotLoad")}
      {...props}
    />
  );
};

export const MappingRuleSingleSelect: FC<
  EntitySearchSingleSelectProps<MappingRule>
> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      search={search}
      getId={getId}
      itemToString={itemToString}
      itemSubTitle={itemSubTitle}
      errorTitle={t("mappingRulesCouldNotLoad")}
      {...props}
    />
  );
};
