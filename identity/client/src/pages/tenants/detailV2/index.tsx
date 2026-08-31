/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { Button, PageHeader } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { tenantQueries } from "src/utility/api/tenants/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
import { Tabs, TabsContent, TabsHeaderList } from "src/components/tabsV2";
import { useEntityModal } from "src/components/modalV2";
import EditModal from "src/pages/tenants/modalsV2/EditModal";
import DeleteModal from "src/pages/tenants/modalsV2/DeleteModal";
import Members from "src/pages/tenants/detailV2/members";
import Groups from "src/pages/tenants/detailV2/groups";
import Roles from "src/pages/tenants/detailV2/roles";
import MappingRules from "src/pages/tenants/detailV2/mapping-rules";
import Clients from "src/pages/tenants/detailV2/clients";
import { isDefaultTenant } from "src/pages/tenants/defaultTenant";

type DetailsProps = {
  isOIDC: boolean;
  isCamundaGroupsEnabled: boolean;
};

const Details: FC<DetailsProps> = ({ isOIDC, isCamundaGroupsEnabled }) => {
  const { t } = useTranslate("tenants");
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();
  const navigate = useNavigate();
  const { data: tenantSearchResults, isLoading: loading } = useQuery(
    tenantQueries.detail({ tenantId: id }),
  );
  const [editTenant, editTenantModal] = useEntityModal(EditModal, () => {});
  const [deleteTenant, deleteTenantModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  const tenant = tenantSearchResults ? tenantSearchResults.items[0] : null;

  if (!loading && !tenant) return <NotFound />;

  return (
    <StackPage>
      <>
        {loading && !tenant ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          tenant && (
            <Tabs
              tabs={[
                {
                  key: "users",
                  label: t("users"),
                  content: (
                    <Members tenantId={tenant.tenantId} isOIDC={isOIDC} />
                  ),
                },
                {
                  key: "groups",
                  label: t("groups"),
                  content: (
                    <Groups
                      tenantId={tenant.tenantId}
                      isCamundaGroupsEnabled={isCamundaGroupsEnabled}
                    />
                  ),
                },
                {
                  key: "roles",
                  label: t("roles"),
                  content: <Roles tenantId={tenant.tenantId} />,
                },
                ...(isOIDC
                  ? [
                      {
                        key: "mapping-rules",
                        label: t("mappingRules"),
                        content: <MappingRules tenantId={tenant.tenantId} />,
                      },
                      {
                        key: "clients",
                        label: t("clients"),
                        content: <Clients tenantId={tenant.tenantId} />,
                      },
                    ]
                  : []),
              ]}
              selectedTabKey={tab}
              path={`../${id}`}
            >
              <PageHeader
                title={tenant.name}
                description={tenant.description ?? undefined}
                breadcrumb={
                  <Breadcrumbs
                    items={[
                      { href: "..", title: t("tenants") },
                      { title: tenant.tenantId },
                    ]}
                  />
                }
                actions={
                  !isDefaultTenant(tenant.tenantId) && (
                    <>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => editTenant(tenant)}
                      >
                        <Pencil aria-hidden="true" />
                        {t("editTenant")}
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        size="sm"
                        onClick={() => deleteTenant(tenant)}
                      >
                        <Trash2 aria-hidden="true" />
                        {t("delete")}
                      </Button>
                    </>
                  )
                }
                tabs={<TabsHeaderList />}
              />
              <TabsContent />
            </Tabs>
          )
        )}
      </>
      {editTenantModal}
      {deleteTenantModal}
    </StackPage>
  );
};

export default Details;
