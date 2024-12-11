/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda
 * Services GmbH under one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC, useState } from "react";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import Page, { PageTitle } from "src/components/layout/Page";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import {
  documentationHref,
  DocumentationLink,
} from "src/components/documentation";
import { searchTenant, Tenant } from "src/utility/api/tenants";
import { useNavigate } from "react-router";
import { TranslatedErrorInlineNotification } from "src/components/notifications/InlineNotification";
import useModal, { useEntityModal } from "src/components/modal/useModal";
import AddModal from "src/pages/tenants/modals/AddModal";
import EditModal from "src/pages/tenants/modals/EditModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Edit, TrashCan } from "@carbon/react/icons";

const List: FC = () => {
  const { t, Translate } = useTranslate();
  const navigate = useNavigate();
  const [, setSearch] = useState("");
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

  if (success && !tenantSearchResults?.items.length) {
    return (
      <Page>
        <PageTitle>
          <Translate>Tenants</Translate>
        </PageTitle>
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
      <EntityList
        title={t("Tenants")}
        data={tenantSearchResults == null ? [] : tenantSearchResults.items}
        headers={[
          { header: t("Name"), key: "name" },
          { header: t("Tenant ID"), key: "tenantId" },
        ]}
        sortProperty="name"
        onEntityClick={showDetails}
        addEntityLabel={t("Create tenant")}
        onAddEntity={addTenant}
        onSearch={setSearch}
        loading={loading}
        menuItems={[
          {
            label: t("Rename"),
            icon: Edit,
            onClick: (tenant) =>
              editTenant({ tenantKey: tenant.tenantKey, name: tenant.name }),
          },
          {
            label: t("Delete"),
            icon: TrashCan,
            isDangerous: true,
            onClick: (tenant) =>
              deleteTenant({ tenantKey: tenant.tenantKey, name: tenant.name }),
          },
        ]}
      />
      {success && (
        <DocumentationDescription>
          <Translate>Learn more about tenants in our</Translate>{" "}
          <DocumentationLink path="/concepts/multi-tenancy/" />.
        </DocumentationDescription>
      )}
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