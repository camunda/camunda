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
import { useApi } from "src/utility/api/hooks";
import { getMembersByTenantId } from "src/utility/api/membership";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import { TrashCan } from "@carbon/react/icons";
import DeleteModal from "src/pages/tenants/detail/members/DeleteModal";
import AssignMembersModal from "src/pages/tenants/detail/members/AssignMembersModal";

type MembersProps = {
  tenantId: string;
};

const Members: FC<MembersProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getMembersByTenantId, {
    tenantId: tenantId,
  });

  const areNoUsersAssigned = !users || users.items?.length === 0;
  const [assignUsers, assignUsersModal] = useEntityModal(
    AssignMembersModal,
    reload,
    {
      assignedUsers: users?.items || [],
    },
  );
  const openAssignModal = () => assignUsers({ id: tenantId });
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
        description={t("unableToLoadMembers")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && areNoUsersAssigned)
    return (
      <>
        <C3EmptyState
          heading={t("assignUsersToTenant")}
          description={t("tenantMemberAccessDisclaimer")}
          button={{
            label: t("assignUser"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutTenants"),
            href: `/identity/concepts/access-control/tenants`,
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
          { header: t("username"), key: "username" },
          { header: t("name"), key: "name" },
          { header: t("email"), key: "email" },
        ]}
        sortProperty="username"
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
      />
      {assignUsersModal}
      {unassignMemberModal}
    </>
  );
};

export default Members;
