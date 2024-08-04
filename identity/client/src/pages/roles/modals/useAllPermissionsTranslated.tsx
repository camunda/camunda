/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Role } from "src/utility/api/roles";
import { default as allPermissions } from "src/configuration/permissions";
import { useState } from "react";
import usePermissionsTranslated, {
  TranslatedPermissionItem,
} from "src/pages/roles/modals/usePermissionsTranslated";

const useAllPermissionsTranslated = (
  initialPermissions: Role["permissions"] = [],
) => {
  const [permissions, setPermissions] = useState<string[]>(initialPermissions);
  const availableItems: TranslatedPermissionItem[] =
    usePermissionsTranslated(allPermissions);

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

export default useAllPermissionsTranslated;
