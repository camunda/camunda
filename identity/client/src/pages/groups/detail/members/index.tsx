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
import { searchMembersByGroup } from "src/utility/api/membership";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import AssignMembersModal from "src/pages/groups/detail/members/AssignMembersModal";
import AssignMemberModal from "src/pages/groups/detail/members/AssignMemberModal";
import DeleteModal from "src/pages/groups/detail/members/DeleteModal";
import { isOIDC } from "src/configuration";
import { useEnrichedUsers } from "src/components/global/useEnrichUsers";
import { UserKeys } from "src/utility/api/users";
import TabEmptyState from "src/components/layout/TabEmptyState";

type MembersProps = {
  groupId: string;
};

const Members: FC<MembersProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");

  const { users, loading, success, reload, paginationProps } = useEnrichedUsers(
    searchMembersByGroup,
    {
      groupId,
    },
  );

  const isUsersListEmpty = !users || users.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    isOIDC ? AssignMemberModal : AssignMembersModal,
    reload,
    { assignedUsers: users },
  );
  const openAssignModal = () => assignUsers({ groupId });
  const [unassignMember, unassignMemberModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      groupId,
    },
  );
  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: t("user").toLowerCase(),
        })}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isUsersListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"user"}
          parentResourceTypeTranslationKey={"group"}
          handleClick={openAssignModal}
          docsLinkPath="/docs/components/identity/user/"
        />
        {assignUsersModal}
      </>
    );

  type MembersListHeaders = {
    header: string;
    key: UserKeys;
    isSortable?: boolean;
  }[];

  const membersListHeaders: MembersListHeaders = isOIDC
    ? [{ header: t("username"), key: "username", isSortable: true }]
    : [
        { header: t("username"), key: "username", isSortable: true },
        { header: t("name"), key: "name" },
        { header: t("email"), key: "email" },
      ];

  return (
    <>
      <EntityList
        data={users}
        headers={membersListHeaders}
        loading={loading}
        addEntityLabel={t("assignUser")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByUsername")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignMember,
          },
        ]}
        {...paginationProps}
      />
      {assignUsersModal}
      {unassignMemberModal}
    </>
  );
};

export default Members;
