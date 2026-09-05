/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Trash2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { usePagination } from "src/utility/api";
import { roleQueries } from "src/utility/api/roles/queries";
import EntityList from "src/components/entityListV2";
import { useEntityModal } from "src/components/modalV2";
import { ErrorInlineNotification } from "src/components/notificationsV2/InlineNotification";
import DeleteModal from "src/pages/roles/detailV2/clients/DeleteModal";
import AssignClientsModal from "src/pages/roles/detailV2/clients/AssignClientsModal";
import TabEmptyState from "src/components/layoutV2/TabEmptyState";
import { useListPollingReload } from "src/utility/hooks/useListPollingReload";
import type { Role } from "@camunda/camunda-api-zod-schemas/8.10";

type ClientsProps = {
  roleId: Role["roleId"];
};

const Clients: FC<ClientsProps> = ({ roleId }) => {
  const { t } = useTranslate("roles");

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: clients,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useQuery({
    ...roleQueries.clients(roleId, pageParams),
    select: (data) => ({
      ...data,
      items: data.items.map((client) => ({ ...client, id: client.clientId })),
    }),
  });

  const assignedClients =
    clients && Array.isArray(clients.items) ? clients.items : [];
  const { startPolling } = useListPollingReload(
    reload,
    assignedClients,
    "clientId",
    clients?.page.totalItems,
  );

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    () => startPolling(1),
  );
  const openAssignModal = () => assignClient(roleId);
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    () => startPolling(-1),
    { roleId },
  );

  if (!loading && !success)
    return (
      <ErrorInlineNotification
        title={t("somethingsWrong")}
        subtitle={t("unableToLoadResource", {
          resourceType: t("client").toLowerCase(),
        })}
        actionButton={{
          label: t("retry"),
          onClick: () => {
            void reload();
          },
        }}
      />
    );

  if (success && assignedClients.length === 0)
    return (
      <>
        <TabEmptyState
          childResourceTypeTranslationKey={"client"}
          parentResourceTypeTranslationKey={"role"}
          handleClick={openAssignModal}
          docsLinkPath="/components/admin/client/"
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
            icon: Trash2,
            isDangerous: true,
            onClick: unassignClient,
          },
        ]}
        page={{ ...page, ...clients?.page }}
        {...paginationCallbacks}
      />
      {assignClientModal}
      {unassignClientModal}
    </>
  );
};

export default Clients;
