/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ReactElement, useState } from "react";
import { Role } from "src/utility/api/roles";
import { default as allPermissions } from "src/configuration/permissions";
import useTranslate from "src/utility/localization";

type RoleListItem = {
  permission: string;
  description: ReactElement | string;
};

const usePermissions = (initialPermissions: Role["permissions"] = []) => {
  const { t } = useTranslate("permissions");
  const [permissions, setPermissions] = useState<string[]>(initialPermissions);

  const availableItems: RoleListItem[] = allPermissions.map((permission) => ({
    permission,
    description: t(`${permissions}.description`),
  }));

  const onSelect = (entity: (typeof availableItems)[0]) => {
    setPermissions([...permissions, entity.permission]);
  };

  const onUnselect = (toRemove: (typeof availableItems)[0]) => {
    setPermissions((selected) =>
      selected.filter((permission) => permission !== toRemove.permission),
    );
  };

  return {
    permissions,
    availableItems,
    setPermissions,
    onSelect,
    onUnselect,
  };
};

export default usePermissions;
