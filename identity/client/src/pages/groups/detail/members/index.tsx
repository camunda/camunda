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
import { getMembersByGroup } from "src/utility/api/membership";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";
import { TrashCan } from "@carbon/react/icons";
import { useEntityModal } from "src/components/modal";
import AssignMembersModal from "src/pages/groups/detail/members/AssignMembersModal";
import DeleteModal from "src/pages/groups/detail/members/DeleteModal";

type MembersProps = {
  groupId: string;
};

const Members: FC<MembersProps> = ({ groupId }) => {
  const { t, Translate } = useTranslate();

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getMembersByGroup, {
    groupId: groupId,
  });

  const areNoUsersAssigned = !users || users.items?.length === 0;
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
      group: groupId,
    },
  );
  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("Something's wrong")}
        description={t(
          'We were unable to load the members. Click "Retry" to try again.',
        )}
        button={{ label: t("Retry"), onClick: reload }}
      />
    );

  if (success && areNoUsersAssigned)
    return (
      <>
        <C3EmptyState
          heading={t("Assign members to this group")}
          description={t(
            "Members of this group will be given access and roles that are assigned to this group.",
          )}
          button={{
            label: t("Assign members"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("Learn more about groups"),
            href: `/identity/concepts/access-control/groups`,
          }}
        />
        {assignUsersModal}
      </>
    );

  return (
    <>
      <EntityList
        title={t("Assigned members")}
        data={users?.items}
        headers={[
          { header: t("Username"), key: "username" },
          { header: t("Email"), key: "email" },
        ]}
        sortProperty="username"
        loading={loading}
        addEntityLabel={t("Assign members")}
        menuItems={[
          {
            label: t("Remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignMember,
          },
        ]}
      />
      {success && !areNoUsersAssigned && (
        <DocumentationDescription>
          <Translate>To learn more, visit our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/groups">
            {t("groups documentation")}
          </DocumentationLink>
          .
        </DocumentationDescription>
      )}
      <>
        {assignUsersModal}
        {unassignMemberModal}
      </>
    </>
  );
};

export default Members;
