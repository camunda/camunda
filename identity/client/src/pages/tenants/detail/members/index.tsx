/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { getMembersByTenantId } from "src/utility/api/membership";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import { TrashCan } from "@carbon/react/icons";
import DeleteModal from "src/pages/tenants/detail/members/DeleteModal";
import AssignMembersModal from "src/pages/tenants/detail/members/AssignMembersModal";
import AssignMemberModal from "src/pages/tenants/detail/members/AssignMemberModal";
import { isOIDC } from "src/configuration";
import { UserKeys } from "src/utility/api/users";
import { useEnrichedUsers } from "src/components/global/useEnrichUsers";
import TabEmptyState from "src/components/layout/TabEmptyState";

type MembersProps = {
  tenantId: string;
};

const Members: FC<MembersProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const CHILD_RESOURCE_TYPE_STRING = t("user").toLowerCase();
  const PARENT_RESOURCE_TYPE_STRING = t("tenant").toLowerCase();

  const { users, loading, success, reload, paginationProps } = useEnrichedUsers(
    getMembersByTenantId,
    {
      tenantId,
    },
  );

  const isAssignedUsersListEmpty = !users || users.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    isOIDC ? AssignMemberModal : AssignMembersModal,
    reload,
    { assignedUsers: users },
  );
  const openAssignModal = () => assignUsers({ tenantId });
  const [unassignMember, unassignMemberModal] = useEntityModal(
    DeleteModal,
    reload,
    {
      tenant: tenantId,
    },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: CHILD_RESOURCE_TYPE_STRING,
        })}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isAssignedUsersListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceType={CHILD_RESOURCE_TYPE_STRING}
          parentResourceType={PARENT_RESOURCE_TYPE_STRING}
          handleClick={openAssignModal}
          docsLinkPath=""
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
