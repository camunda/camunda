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
import { userQueries } from "src/utility/api/users/queries";
import type { User } from "@camunda/camunda-api-zod-schemas/8.10";

const getId = (user: User) => user.username;
const itemToString = (user: User) => user.name || user.username;
const itemSubTitle = (user: User) => user.email;
const search = (search: string) =>
  userQueries.search(
    search === "" ? {} : { filter: { username: { $like: `*${search}*` } } },
  );

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
      search={search}
      getId={getId}
      itemSubTitle={itemSubTitle}
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
      search={search}
      getId={getId}
      itemToString={itemToString}
      itemSubTitle={itemSubTitle}
      errorTitle={t("usersCouldNotLoad")}
      {...props}
    />
  );
};
