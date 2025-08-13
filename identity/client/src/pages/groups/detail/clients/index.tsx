/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { TrashCan } from "@carbon/react/icons";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import { getClientsByGroupId, Group } from "src/utility/api/groups";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/groups/detail/clients/DeleteModal";
import AssignClientsModal from "src/pages/groups/detail/clients/AssignClientsModal";
import { docsUrl } from "src/configuration";

type ClientsProps = {
  groupId: Group["groupId"];
};

const Clients: FC<ClientsProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");

  const { data, loading, success, reload, ...paginationProps } =
    usePaginatedApi(getClientsByGroupId, {
      groupId,
    });

  const assignedClients = data && Array.isArray(data.items) ? data.items : [];

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    reload,
  );
  const openAssignModal = () => assignClient({ groupId });
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    reload,
    { groupId },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadClients")}
        button={{ label: t("retry"), onClick: reload }}
      />
    );

  if (success && assignedClients.length === 0)
    return (
      <>
        <C3EmptyState
          heading={t("assignClientsToGroup")}
          description={t("membersAccessDisclaimer")}
          button={{
            label: t("assignClient"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutGroups"),
            href: docsUrl,
          }}
        />
        {assignClientModal}
      </>
    );

  return (
    <>
      <EntityList
        data={assignedClients}
        headers={[{ header: t("clientId"), key: "clientId", isSortable: true }]}
        loading={loading}
        addEntityLabel={t("assignClient")}
        onAddEntity={openAssignModal}
        searchPlaceholder={t("searchByClientId")}
        menuItems={[
          {
            label: t("remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: unassignClient,
          },
        ]}
        {...paginationProps}
      />
      {assignClientModal}
      {unassignClientModal}
    </>
  );
};

export default Clients;
