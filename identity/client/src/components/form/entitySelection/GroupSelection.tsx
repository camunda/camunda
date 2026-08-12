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
import GroupSearchDropdown from "src/components/form/GroupSearchDropdown";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (group: Group) => group.groupId;
const itemToString = (group: Group) => group.name || group.groupId;

type GroupMultiSelectProps = {
  value: Group[];
  onChange: (groups: Group[]) => void;
  excluded?: Group[];
  autoFocus?: boolean;
};

export const GroupMultiSelect: FC<GroupMultiSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      searchDropdown={GroupSearchDropdown}
      getId={getId}
      placeholder={t("searchByGroupId")}
      errorTitle={t("groupsCouldNotLoad")}
      {...props}
    />
  );
};

type GroupSingleSelectProps = {
  label: string;
  placeholder: string;
  requiredText: string;
  onChange: (groupId: string) => void;
  value?: string;
  isEmpty?: boolean;
};

export const GroupSingleSelect: FC<GroupSingleSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      searchDropdown={GroupSearchDropdown}
      getId={getId}
      itemToString={itemToString}
      errorTitle={t("groupsCouldNotLoad")}
      {...props}
    />
  );
};
