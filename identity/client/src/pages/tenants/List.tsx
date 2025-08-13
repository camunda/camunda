/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { useNavigate } from "react-router";
import useTranslate from "src/utility/localization";
import { usePaginatedApi } from "src/utility/api";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import PageEmptyState from "src/components/layout/PageEmptyState";

const List: FC = () => {
  const { t } = useTranslate("tenants");
  const RESOURCE_TYPE_STRING = t("tenant").toLowerCase();
  const navigate = useNavigate();
  const {
    data: tenantSearchResults,
    loading,
    reload,
    success,
    search,
    ...paginationProps
  } = usePaginatedApi(searchTenant);

  const [addTenant, addTenantModal] = useModal(AddModal, reload);
  const [deleteTenant, deleteTenantModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ tenantId }: Tenant) => navigate(`${tenantId}`);

  const shouldShowEmptyState =
    success && !search && !tenantSearchResults?.items.length;

  const pageHeader = (
    <PageHeader
      title={t("tenants")}
      linkText={t("tenants").toLowerCase()}
      docsLinkPath=""
      shouldShowDocumentationLink={!shouldShowEmptyState}
    />
  );

  if (shouldShowEmptyState) {
    return (
      <Page>
        {pageHeader}
        <PageEmptyState
          resourceType={RESOURCE_TYPE_STRING}
          docsLinkPath=""
          handleClick={addTenant}
        />
        {addTenantModal}
      </Page>
    );
  }

  return (
    <Page>
      {pageHeader}
      <EntityList
        data={tenantSearchResults == null ? [] : tenantSearchResults.items}
        headers={[
          { header: t("tenantId"), key: "tenantId", isSortable: true },
          { header: t("name"), key: "name", isSortable: true },
        ]}
        onEntityClick={showDetails}
        addEntityLabel={t("createTenant")}
        onAddEntity={addTenant}
        loading={loading}
        menuItems={[
          {
            label: t("delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: (tenant) =>
              deleteTenant({
                tenantId: tenant.tenantId,
                name: tenant.name,
                description: tenant.description,
              }),
          },
        ]}
        searchPlaceholder={t("searchByTenantId")}
        searchKey="tenantId"
        {...paginationProps}
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("tenantsListCouldNotLoad")}
          actionButton={{ label: t("retry"), onClick: reload }}
        />
      )}
      {addTenantModal}
      {deleteTenantModal}
    </Page>
  );
};

export default List;
