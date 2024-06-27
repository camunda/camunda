/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ReactElement } from "react";
import useTranslate from "src/utility/localization";

type TranslatedPermissionItem = {
  permission: string;
  description: ReactElement | string;
};

const usePermissionsTranslated = (initialPermissions: string[] = []) => {
  const { t } = useTranslate("permissions");
  const translatedItems: TranslatedPermissionItem[] = initialPermissions.map(
    (permission: string) => ({
      permission,
      description: t(`${permission}.description`),
    }),
  );
  return translatedItems;
};

export type { TranslatedPermissionItem };
export default usePermissionsTranslated;
