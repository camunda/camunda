/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { OwnerType } from "src/utility/api/authorizations";
import { searchUser } from "src/utility/api/users";
import { searchGroups } from "src/utility/api/groups";
import { searchMappingRule } from "src/utility/api/mapping-rules";
import { searchRoles } from "src/utility/api/roles";
import useTranslate from "src/utility/localization";
import OwnerSelection from "./OwnerSelection";
import TextField from "src/components/form/TextField";
import { isCamundaGroupsEnabled, isOIDC } from "src/configuration";

type SelectionProps = {
  type: OwnerType;
  ownerId: string;
  onChange: (newOwner: string) => void;
};

const Selection: FC<SelectionProps> = ({ type, ownerId, onChange }) => {
  const { t } = useTranslate("authorizations");

  switch (type) {
    case OwnerType.USER:
      if (isOIDC) {
        return (
          <TextField
            value={ownerId}
            label={t("username")}
            onChange={onChange}
            placeholder={t("enterUsername")}
            type="text"
          />
        );
      }
      return (
        <OwnerSelection
          id="userSelection"
          onChange={onChange}
          searchFn={searchUser}
          getId={(user) => user.username}
          itemToString={(user) => user.name || user.username}
        />
      );
    case OwnerType.GROUP:
      if (isCamundaGroupsEnabled) {
        return (
          <OwnerSelection
            id="groupSelection"
            onChange={onChange}
            searchFn={searchGroups}
            getId={(group) => group.groupId}
            itemToString={(group) => group.name || group.groupId}
          />
        );
      }
      return (
        <TextField
          value={ownerId}
          label={t("groupId")}
          onChange={onChange}
          placeholder={t("enterGroupId")}
          type="text"
        />
      );
    case OwnerType.MAPPING_RULE:
      return (
        <OwnerSelection
          id="mappingRuleSelection"
          onChange={onChange}
          searchFn={searchMappingRule}
          getId={(mappingRule) => mappingRule.mappingRuleId}
          itemToString={(mappingRule) =>
            mappingRule.name || mappingRule.mappingRuleId
          }
        />
      );
    case OwnerType.ROLE:
      return (
        <OwnerSelection
          id="roleSelection"
          onChange={onChange}
          searchFn={searchRoles}
          getId={(role) => role.roleId}
          itemToString={(role) => role.name || role.roleId}
        />
      );
    case OwnerType.CLIENT:
      return (
        <TextField
          value={ownerId}
          label={t("owner")}
          onChange={onChange}
          placeholder={t("enterId")}
          type="text"
        />
      );
    default:
      return null;
  }
};

export default Selection;
