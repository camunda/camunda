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
import { getClientsByTenantId, Tenant } from "src/utility/api/tenants";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/tenants/detail/clients/DeleteModal";
import AssignClientsModal from "src/pages/tenants/detail/clients/AssignClientsModal";
import TabEmptyState from "src/components/layout/TabEmptyState";

type ClientsProps = {
  tenantId: Tenant["tenantId"];
};

const Clients: FC<ClientsProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const CHILD_RESOURCE_TYPE_STRING = t("client").toLowerCase();
  const PARENT_RESOURCE_TYPE_STRING = t("tenant").toLowerCase();

  const {
    data: clients,
    loading,
    success,
    reload,
    ...paginationProps
  } = usePaginatedApi(getClientsByTenantId, {
    tenantId,
  });

  const assignedClients =
    clients && Array.isArray(clients.items) ? clients.items : [];

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    reload,
  );
  const openAssignModal = () => assignClient(tenantId);
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    reload,
    { tenantId },
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

  if (success && assignedClients.length === 0)
    return (
      <>
        <TabEmptyState
          childResourceType={CHILD_RESOURCE_TYPE_STRING}
          parentResourceType={PARENT_RESOURCE_TYPE_STRING}
          handleClick={openAssignModal}
          docsLinkPath=""
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
