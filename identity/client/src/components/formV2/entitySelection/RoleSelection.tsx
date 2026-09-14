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
} from "src/components/formV2/EntitySearchMultiSelect";
import EntitySearchSingleSelect, {
  type EntitySearchSingleSelectProps,
} from "src/components/formV2/EntitySearchSingleSelect";
import { roleQueries } from "src/utility/api/roles/queries";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (role: Role) => role.roleId;
const itemLabel = (role: Role) => `${role.roleId} — ${role.name}`;
const search = (search: string) =>
  roleQueries.search(
    search === ""
      ? {}
      : {
          filter: {
            $or: [
              { name: { $like: `*${search}*` } },
              { roleId: { $like: `*${search}*` } },
            ],
          },
        },
  );

export const RoleMultiSelect: FC<EntitySearchMultiSelectProps<Role>> = (
  props,
) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      search={search}
      getId={getId}
      itemLabel={itemLabel}
      placeholder={t("searchByRoleId")}
      errorTitle={t("rolesCouldNotLoad")}
      {...props}
    />
  );
};

export const RoleSingleSelect: FC<EntitySearchSingleSelectProps<Role>> = (
  props,
) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      search={search}
      getId={getId}
      itemLabel={itemLabel}
      errorTitle={t("rolesCouldNotLoad")}
      {...props}
    />
  );
};
