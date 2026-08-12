/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import useTranslate from "src/utility/localization";
import TextField from "src/components/form/TextField";
import { UserSingleSelect } from "src/components/form/entitySelection/UserSelection";
import { GroupSingleSelect } from "src/components/form/entitySelection/GroupSelection";
import { RoleSingleSelect } from "src/components/form/entitySelection/RoleSelection";
import { MappingRuleSingleSelect } from "src/components/form/entitySelection/MappingRuleSelection";
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
          <UserSingleSelect
            label={t("owner")}
            placeholder={t("searchByOwnerId")}
            requiredText={t("ownerRequired")}
            onChange={onChange}
            value={ownerId}
            isEmpty={isEmpty}
          />
        </div>
      );
    case "GROUP":
      if (isCamundaGroupsEnabled) {
        return (
          <div onBlur={onBlur}>
            <GroupSingleSelect
              label={t("owner")}
              placeholder={t("searchByOwnerId")}
              requiredText={t("ownerRequired")}
              onChange={onChange}
              value={ownerId}
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
          <MappingRuleSingleSelect
            label={t("owner")}
            placeholder={t("searchByOwnerId")}
            requiredText={t("ownerRequired")}
            onChange={onChange}
            value={ownerId}
            isEmpty={isEmpty}
          />
        </div>
      );
    case "ROLE":
      return (
        <div onBlur={onBlur}>
          <RoleSingleSelect
            label={t("owner")}
            placeholder={t("searchByOwnerId")}
            requiredText={t("ownerRequired")}
            onChange={onChange}
            value={ownerId}
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
