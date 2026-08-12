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
import UserSearchDropdown from "src/components/form/UserSearchDropdown";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (user: User) => user.username;
const itemToString = (user: User) => user.name || user.username;

type UserMultiSelectProps = {
  value: User[];
  onChange: (users: User[]) => void;
  excluded?: User[];
  autoFocus?: boolean;
};

export const UserMultiSelect: FC<UserMultiSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchMultiSelect
      searchDropdown={UserSearchDropdown}
      getId={getId}
      placeholder={t("searchByUsernameOrEmail")}
      errorTitle={t("usersCouldNotLoad")}
      {...props}
    />
  );
};

type UserSingleSelectProps = {
  label: string;
  placeholder: string;
  requiredText: string;
  onChange: (username: string) => void;
  value?: string;
  isEmpty?: boolean;
};

export const UserSingleSelect: FC<UserSingleSelectProps> = (props) => {
  const { t } = useTranslate("entitySelection");
  return (
    <EntitySearchSingleSelect
      searchDropdown={UserSearchDropdown}
      getId={getId}
      itemToString={itemToString}
      errorTitle={t("usersCouldNotLoad")}
      {...props}
    />
  );
};
