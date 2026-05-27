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
import { usePagination } from "src/utility/api";
import { useTenantClients } from "src/utility/api/tenants/hooks";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/tenants/detail/clients/DeleteModal";
import AssignClientsModal from "src/pages/tenants/detail/clients/AssignClientsModal";
import TabEmptyState from "src/components/layout/TabEmptyState";
import type { Tenant } from "@camunda/camunda-api-zod-schemas/8.10";

type ClientsProps = {
  tenantId: Tenant["tenantId"];
};

const Clients: FC<ClientsProps> = ({ tenantId }) => {
  const { t } = useTranslate("tenants");
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data: clients,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useTenantClients(tenantId, pageParams);

  const assignedClients =
    clients && Array.isArray(clients.items) ? clients.items : [];

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    noop,
  );
  const openAssignModal = () => assignClient(tenantId);
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    noop,
    { tenantId },
  );

  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("somethingsWrong")}
        description={t("unableToLoadResource", {
          resourceType: t("client").toLowerCase(),
        })}
        button={{
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
          parentResourceTypeTranslationKey={"tenant"}
          handleClick={openAssignModal}
          description={t("emptyStateTenantAccessDisclaimer")}
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
            icon: TrashCan,
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
