/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import { getMembersByRole } from "src/utility/api/membership";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import AssignMembersModal from "src/pages/roles/detail/members/AssignMembersModal";
import DeleteModal from "src/pages/roles/detail/members/DeleteModal";
import { isOIDC } from "src/configuration";
import { UserKeys } from "src/utility/api/users";

type MembersProps = {
  roleId: string;
};

const Members: FC<MembersProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getMembersByRole, {
    roleId,
  });

  const isUsersListEmpty = !users || users.items?.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    AssignMembersModal,
    reload,
    {
      assignedUsers: users?.items || [],
    },
  );
  const openAssignModal = () => assignUsers({ id: roleId });
  const [unassignMember, unassignMemberModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      roleId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadMembers")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isUsersListEmpty)
    return (
      <>
        <C3EmptyState
          heading={t("assignUsersToRole")}
          description={t("accessDisclaimer")}
          button={{
            label: t("assignUser"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutRoles"),
            href: `/identity/concepts/access-control/roles`,
          }}
        />
        {assignUsersModal}
      </>
    );

  type MembersListHeaders = {
    header: string;
    key: UserKeys;
  }[];

  const membersListHeaders: MembersListHeaders = isOIDC
    ? [{ header: t("username"), key: "username" }]
    : [
        { header: t("username"), key: "username" },
        { header: t("name"), key: "name" },
        { header: t("email"), key: "email" },
      ];

  return (
    <>
      <EntityList
        data={users?.items}
        headers={membersListHeaders}
        sortProperty="username"
        loading={loading}
        addEntityLabel={t("assignUser")}
        searchPlaceholder={t("searchByUsername")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignMember,
          },
        ]}
      />
      {assignUsersModal}
      {unassignMemberModal}
    </>
  );
};

export default Members;
