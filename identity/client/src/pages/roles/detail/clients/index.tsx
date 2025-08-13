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
import { getClientsByRoleId, Role } from "src/utility/api/roles";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/roles/detail/clients/DeleteModal";
import AssignClientsModal from "src/pages/roles/detail/clients/AssignClientsModal";
import { docsUrl } from "src/configuration";

type ClientsProps = {
  roleId: Role["roleId"];
};

const Clients: FC<ClientsProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");

  const {
    data: clients,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(getClientsByRoleId, {
    roleId,
  });

  const assignedClients =
    clients && Array.isArray(clients.items) ? clients.items : [];

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    reload,
  );
  const openAssignModal = () => assignClient(roleId);
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    reload,
    { roleId },
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
          heading={t("assignClientsToRole")}
          description={t("accessDisclaimer")}
          button={{
            label: t("assignClient"),
            onClick: openAssignModal,
          }}
          link={{
            label: t("learnMoreAboutRoles"),
            href: docsUrl,
          }}
        />
        {assignClientModal}
      </>
    );

  return (
    <>
      <EntityList
        data={clients?.items}
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
