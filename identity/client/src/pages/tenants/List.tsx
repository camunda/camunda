/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { TrashCan } from "@carbon/react/icons";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { useNavigate } from "react-router";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageHeader } from "src/components/layout/Page";
import EntityList from "src/components/entityList";
import { documentationHref } from "src/components/documentation";
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";

const List: FC = () => {
  const { t } = useTranslate("tenants");
  const navigate = useNavigate();
  const {
    data: tenantSearchResults,
    loading,
    reload,
    success,
  } = useApi(searchTenant);

  const [addTenant, addTenantModal] = useModal(AddModal, reload);
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
          heading={t("noTenants")}
          description={t("createIsolatedEnvironments")}
          button={{
            label: t("createATenant"),
            onClick: addTenant,
          }}
          link={{
            href: documentationHref("/concepts/multi-tenancy/", ""),
            label: t("learnMoreAboutTenants"),
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
          { header: t("tenantId"), key: "tenantId" },
          { header: t("name"), key: "name" },
        ]}
        sortProperty="name"
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
