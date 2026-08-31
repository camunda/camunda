/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Trash2 } from "lucide-react";
import useTranslate from "src/utility/localization";
import { getMembersByTenantId } from "src/utility/api/membership";
import EntityList from "src/components/entityListV2";
import { useEntityModal } from "src/components/modalV2";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import DeleteModal from "src/pages/tenants/detailV2/members/DeleteModal";
import AssignMembersModal from "src/pages/tenants/detailV2/members/AssignMembersModal";
import AssignMemberModal from "src/pages/tenants/detailV2/members/AssignMemberModal";
import { UserKeys } from "src/utility/api/users";
import { useEnrichedUsers } from "src/components/global/useEnrichUsers";
import TabEmptyState from "src/components/layoutV2/TabEmptyState";

type MembersProps = {
  tenantId: string;
  isOIDC: boolean;
};

const Members: FC<MembersProps> = ({ tenantId, isOIDC }) => {
  const { t } = useTranslate("tenants");

  const { users, loading, success, reload, paginationProps } = useEnrichedUsers(
    "tenants",
    getMembersByTenantId,
    {
      tenantId,
    },
    isOIDC,
  );
  const noop = () => {};

  const isAssignedUsersListEmpty = !users || users.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    isOIDC ? AssignMemberModal : AssignMembersModal,
    noop,
    { assignedUsers: users },
  );
  const openAssignModal = () => assignUsers({ tenantId });
  const [unassignMember, unassignMemberModal] = useEntityModal(
    DeleteModal,
    noop,
    {
      tenant: tenantId,
    },
  );

  if (!loading && !success)
    return (
      <ErrorInlineNotification
        title={t("somethingsWrong")}
        subtitle={t("unableToLoadResource", {
          resourceType: t("user").toLowerCase(),
        })}
        actionButton={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && isAssignedUsersListEmpty)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"user"}
          parentResourceTypeTranslationKey={"tenant"}
          handleClick={openAssignModal}
          description={t("emptyStateTenantAccessDisclaimer")}
          docsLinkPath="/components/admin/user/"
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
            icon: Trash2,
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
