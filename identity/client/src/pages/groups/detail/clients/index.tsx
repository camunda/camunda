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
import { useGroupClients } from "src/utility/api/groups/hooks";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/groups/detail/clients/DeleteModal";
import AssignClientsModal from "src/pages/groups/detail/clients/AssignClientsModal";
import TabEmptyState from "src/components/layout/TabEmptyState";
import type { Group } from "@camunda/camunda-api-zod-schemas/8.10";

type ClientsProps = {
  groupId: Group["groupId"];
};

const Clients: FC<ClientsProps> = ({ groupId }) => {
  const { t } = useTranslate("groups");
  const noop = () => {};

  const { pageParams, page, ...paginationCallbacks } = usePagination();
  const {
    data,
    isLoading: loading,
    isSuccess: success,
    refetch: reload,
  } = useGroupClients(groupId, pageParams);

  const assignedClients = data && Array.isArray(data.items) ? data.items : [];

  const [assignClient, assignClientModal] = useEntityModal(
    AssignClientsModal,
    noop,
  );
  const openAssignModal = () => assignClient({ groupId });
  const [unassignClient, unassignClientModal] = useEntityModal(
    DeleteModal,
    noop,
    { groupId },
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
          parentResourceTypeTranslationKey={"group"}
          handleClick={openAssignModal}
          docsLinkPath="/components/admin/client/"
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
        page={{ ...page, ...data?.page }}
        {...paginationCallbacks}
      />
      {assignClientModal}
      {unassignClientModal}
    </>
  );
};

export default Clients;
