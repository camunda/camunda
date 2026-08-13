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
import { roleQueries } from "src/utility/api/roles/queries";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (role: Role) => role.roleId;
const itemToString = (role: Role) => role.name || role.roleId;
const itemSubTitle = (role: Role) => role.name;
const search = (search: string) =>
  roleQueries.search(search === "" ? {} : { filter: { name: search } });

type RoleMultiSelectProps = {
  value: Role[];
  onChange: (roles: Role[]) => void;
  excluded?: Role[];
  autoFocus?: boolean;
};

export const RoleMultiSelect: FC<RoleMultiSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      search={search}
      getId={getId}
      itemSubTitle={itemSubTitle}
      placeholder={t("searchByRoleId")}
      errorTitle={t("rolesCouldNotLoad")}
      {...props}
    />
  );
};

type RoleSingleSelectProps = {
  label: string;
  placeholder: string;
  requiredText: string;
  onChange: (roleId: string) => void;
  value?: string;
  isEmpty?: boolean;
};

export const RoleSingleSelect: FC<RoleSingleSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      search={search}
      getId={getId}
      itemToString={itemToString}
      itemSubTitle={itemSubTitle}
      errorTitle={t("rolesCouldNotLoad")}
      {...props}
    />
  );
};
