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
import RoleSearchDropdown from "src/components/form/RoleSearchDropdown";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (role: Role) => role.roleId;
const itemToString = (role: Role) => role.name || role.roleId;

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
      searchDropdown={RoleSearchDropdown}
      getId={getId}
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
      searchDropdown={RoleSearchDropdown}
      getId={getId}
      itemToString={itemToString}
      errorTitle={t("rolesCouldNotLoad")}
      {...props}
    />
  );
};
