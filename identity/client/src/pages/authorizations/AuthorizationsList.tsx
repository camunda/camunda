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
            { header: t("ownerType"), key: "ownerType" },
            { header: t("ownerId"), key: "ownerId" },
            { header: t("resourceId"), key: "resourceId" },
            { header: t("permissionTypes"), key: "permissionTypes" },
          ]}
          addEntityLabel={t("createAuthorization")}
          onAddEntity={() => {
            addAuthorization(tab);
          }}
          loading={loading}
          menuItems={[
            {
              label: t("delete"),
              icon: TrashCan,
              isDangerous: true,
              onClick: deleteAuthorization,
            },
          ]}
        />
      ) : (
        <C3EmptyState
          heading={t("noAuthorizationsYet", {
            tab: t(tab),
          })}
          description={t("authorizationDescription")}
          button={{
            label: t("createAuthorization"),
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
