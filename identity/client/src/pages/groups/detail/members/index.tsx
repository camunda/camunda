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
import { getMembersByGroup } from "src/utility/api/membership";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import AssignMembersModal from "src/pages/groups/detail/members/AssignMembersModal";
import DeleteModal from "src/pages/groups/detail/members/DeleteModal";

type MembersProps = {
  groupId: string;
};

const Members: FC<MembersProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getMembersByGroup, {
    groupId: groupId,
  });

  const isUsersListEmpty = !users || users.items?.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    AssignMembersModal,
    reload,
    {
      assignedUsers: users?.items || [],
    },
  );
  const openAssignModal = () => assignUsers({ id: groupId });
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
        description={t("unableToLoadMembers")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isUsersListEmpty)
    return (
      <>
        <C3EmptyState
          heading={t("assignUsersToGroup")}
          description={t(
            "Members of this group will be given access and roles that are assigned to this group.",
          )}
          button={{
            label: t("assignUser"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutGroups"),
            href: `/identity/concepts/access-control/groups`,
          }}
        />
        {assignUsersModal}
      </>
    );

  return (
    <>
      <EntityList
        data={users?.items}
        headers={[
          { header: t("userId"), key: "key" },
          { header: t("username"), key: "username" },
        ]}
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
