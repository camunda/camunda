/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import OwnerSelectionSearch from "./OwnerSelectionSearch";
import TextField from "src/components/form/TextField";
import GroupSearchDropdown from "src/components/form/GroupSearchDropdown";
import MappingRuleSearchDropdown from "src/components/form/MappingRuleSearchDropdown";
import RoleSearchDropdown from "src/components/form/RoleSearchDropdown";
import UserSearchDropdown from "src/components/form/UserSearchDropdown";
import { Caption } from "src/pages/authorizations/modals/components.tsx";
import { DocumentationLink } from "src/components/documentation";
import { getIdPattern } from "src/utility/validate";
import type { Authorization } from "@camunda/camunda-api-zod-schemas/8.10";

type SelectionProps = {
  type: Authorization["ownerType"];
  ownerId: string;
  onChange: (newOwner: string) => void;
  onBlur: () => void;
  isEmpty?: boolean;
  isInvalidId?: boolean;
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
};

const Selection: FC<SelectionProps> = ({
  type,
  ownerId,
  onChange,
  onBlur,
  isEmpty = false,
  isInvalidId = false,
  isOIDC,
  isCamundaGroupsEnabled,
}) => {
  const { t, Translate } = useTranslate("authorizations");

  switch (type) {
    case "USER":
      if (isOIDC) {
        return (
          <TextField
            value={ownerId}
            label={t("username")}
            onChange={onChange}
            onBlur={onBlur}
            placeholder={t("enterUsername")}
            helperText={
              <Caption>
                <Translate i18nKey="usernameDescription">
                  Check the documentation for{" "}
                  <DocumentationLink
                    path="/components/admin/authorization/#about-authorizations"
                    withIcon
                  >
                    how to reference users
                  </DocumentationLink>{" "}
                  .
                </Translate>
              </Caption>
            }
            type="text"
            errors={
              isEmpty
                ? t("usernameRequired")
                : isInvalidId
                  ? t("pleaseEnterValidUsername", {
                      pattern: getIdPattern(),
                    })
                  : ""
            }
          />
        );
      }
      return (
        <div onBlur={onBlur}>
          <OwnerSelectionSearch
            searchDropdown={UserSearchDropdown}
            getId={(user) => user.username}
            itemToString={(user) => user.name || user.username}
            errorTitle={t("usersCouldNotLoad")}
            onChange={onChange}
            ownerId={ownerId}
            isEmpty={isEmpty}
          />
        </div>
      );
    case "GROUP":
      if (isCamundaGroupsEnabled) {
        return (
          <div onBlur={onBlur}>
            <OwnerSelectionSearch
              searchDropdown={GroupSearchDropdown}
              getId={(group) => group.groupId}
              itemToString={(group) => group.name || group.groupId}
              errorTitle={t("groupsCouldNotLoad")}
              onChange={onChange}
              ownerId={ownerId}
              isEmpty={isEmpty}
            />
          </div>
        );
      }
      return (
        <TextField
          value={ownerId}
          label={t("groupId")}
          onChange={onChange}
          onBlur={onBlur}
          placeholder={t("enterGroupId")}
          type="text"
          errors={
            isEmpty
              ? t("groupIdRequired")
              : isInvalidId
                ? t("pleaseEnterValidGroupId", {
                    pattern: getIdPattern(),
                  })
                : ""
          }
        />
      );
    case "MAPPING_RULE":
      return (
        <div onBlur={onBlur}>
          <OwnerSelectionSearch
            searchDropdown={MappingRuleSearchDropdown}
            getId={(mappingRule) => mappingRule.mappingRuleId}
            itemToString={(mappingRule) =>
              mappingRule.name || mappingRule.mappingRuleId
            }
            errorTitle={t("mappingRulesCouldNotLoad")}
            onChange={onChange}
            ownerId={ownerId}
            isEmpty={isEmpty}
          />
        </div>
      );
    case "ROLE":
      return (
        <div onBlur={onBlur}>
          <OwnerSelectionSearch
            searchDropdown={RoleSearchDropdown}
            getId={(role) => role.roleId}
            itemToString={(role) => role.name || role.roleId}
            errorTitle={t("rolesCouldNotLoad")}
            onChange={onChange}
            ownerId={ownerId}
            isEmpty={isEmpty}
          />
        </div>
      );
    case "CLIENT":
      return (
        <TextField
          value={ownerId}
          label={t("owner")}
          onChange={onChange}
          onBlur={onBlur}
          placeholder={t("enterId")}
          type="text"
          errors={
            isEmpty
              ? t("ownerRequired")
              : isInvalidId
                ? t("pleaseEnterValidClientId", {
                    pattern: getIdPattern(),
                  })
                : ""
          }
        />
      );
    case "UNSPECIFIED":
    default:
      return null;
  }
};

export default Selection;
