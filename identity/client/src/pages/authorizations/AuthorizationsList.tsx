/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Loading } from "@carbon/react";
import { Add, TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Authorization, ResourceType } from "src/utility/api/authorizations";
import { SearchResponse } from "src/utility/api";
import useTranslate from "src/utility/localization";
import EntityList from "src/components/entityList";
import { useEntityModal } from "src/components/modal/useModal";
import AddModal from "./modals/AddModal";
import DeleteModal from "./modals/DeleteModal";
import { LoadingWrapper } from "./components";

type AuthorizationListProps = {
  tab: ResourceType;
  data: SearchResponse<Authorization> | null;
  loading: boolean;
  reload: () => unknown;
};

const AuthorizationList: FC<AuthorizationListProps> = ({
  tab,
  data,
  loading,
  reload,
}) => {
  const { t } = useTranslate("authorizations");

  const [addAuthorization, addAuthorizationModal] = useEntityModal(
    AddModal,
    reload,
  );
  const [deleteAuthorization, deleteAuthorizationModal] = useEntityModal(
    DeleteModal,
    reload,
  );

  if (loading)
    return (
      <LoadingWrapper>
        <Loading withOverlay={false} />
      </LoadingWrapper>
    );

  return (
    <>
      {data?.items?.length ? (
        <EntityList
          title={t(tab)}
          data={data.items}
          headers={[
            { header: t("Owner type"), key: "ownerType" },
            { header: t("Owner ID"), key: "ownerId" },
            { header: t("Resource ID"), key: "resourceId" },
            { header: t("Permissions"), key: "permissionTypes" },
          ]}
          addEntityLabel={t("Create authorization")}
          onAddEntity={() => {
            addAuthorization(tab);
          }}
          loading={loading}
          menuItems={[
            {
              label: t("Delete"),
              icon: TrashCan,
              isDangerous: true,
              onClick: deleteAuthorization,
            },
          ]}
        />
      ) : (
        <C3EmptyState
          heading={t(
            "You don't have any authorizations for this resource type yet",
            {
              tab: t(tab),
            },
          )}
          description={t(
            "Authorizations define access permissions for different resources. Create an authorization by selecting an owner, resource, and at least one permission.",
          )}
          button={{
            label: t("Create an authorization"),
            onClick: () => {
              addAuthorization(tab);
            },
            icon: Add,
          }}
        />
      )}
      {addAuthorizationModal}
      {deleteAuthorizationModal}
    </>
  );
};

export default AuthorizationList;
