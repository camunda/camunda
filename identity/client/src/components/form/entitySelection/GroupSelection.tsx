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
import { groupQueries } from "src/utility/api/groups/queries";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (group: Group) => group.groupId;
const itemToString = (group: Group) => group.name || group.groupId;
const itemSubTitle = (group: Group) => group.name;
const search = (search: string) =>
  groupQueries.search(
    search === "" ? {} : { filter: { groupId: { $like: `*${search}*` } } },
  );

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
      search={search}
      getId={getId}
      itemSubTitle={itemSubTitle}
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
      search={search}
      getId={getId}
      itemToString={itemToString}
      itemSubTitle={itemSubTitle}
      errorTitle={t("groupsCouldNotLoad")}
      {...props}
    />
  );
};
