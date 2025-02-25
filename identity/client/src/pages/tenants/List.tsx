/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { Edit, TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { documentationHref } from "src/components/documentation";
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import EditModal from "src/pages/tenants/modals/EditModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";

const List: FC = () => {
  const { t } = useTranslate();
  const navigate = useNavigate();
  const {
    data: tenantSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchTenant);

  const [addTenant, addTenantModal] = useModal(AddModal, reload);
  const [editTenant, editTenantModal] = useEntityModal(EditModal, reload);
  const [deleteTenant, deleteTenantModal] = useEntityModal(DeleteModal, reload);

  const showDetails = ({ tenantId }: Tenant) => navigate(`${tenantId}`);

  const pageHeader = (
    <PageHeader
      title="Tenants"
      linkText="tenants"
      linkUrl="/concepts/tenants/"
    />
  );

  if (success && !tenantSearchResults?.items.length) {
    return (
      <Page>
        {pageHeader}
        <C3EmptyState
          heading={t("You don’t have any tenants yet")}
          description={t(
            "Create isolated environments with their own assigned users, groups, and applications.",
          )}
          button={{
            label: t("Create a tenant"),
            onClick: addTenant,
          }}
          link={{
            href: documentationHref("/concepts/multi-tenancy/", ""),
            label: t("Learn more about tenants"),
          }}
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
          { header: t("Name"), key: "name" },
          { header: t("Tenant ID"), key: "tenantId" },
        ]}
        sortProperty="name"
        onEntityClick={showDetails}
        addEntityLabel={t("Create tenant")}
        onAddEntity={addTenant}
        loading={loading}
        menuItems={[
          {
            label: t("Rename"),
            icon: Edit,
            onClick: (tenant) =>
              editTenant({
                tenantId: tenant.tenantId,
                name: tenant.name,
                description: tenant.description,
              }),
          },
          {
            label: t("Delete"),
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
      />
      {!loading && !success && (
        <TranslatedErrorInlineNotification
          title={t("The list of tenants could not be loaded.")}
          actionButton={{ label: t("Retry"), onClick: reload }}
        />
      )}
      {addTenantModal}
      {editTenantModal}
      {deleteTenantModal}
    </Page>
  );
};

export default List;
