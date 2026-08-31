/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { OverflowMenu, OverflowMenuItem, Section, Stack } from "@carbon/react";
import { useQuery } from "@tanstack/react-query";
import useTranslate from "src/utility/localization";
import { tenantQueries } from "src/utility/api/tenants/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
// TODO: Replace with PageHeader during migration. See roles/detailV2/index.tsx for example.
import PageHeadline from "src/components/layoutV2/PageHeadline";
// TODO: Replace with V2 tabs. See roles/detailV2/index.tsx for example.
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
// TODO: Replace with Tailwind classes.
import Flex from "src/components/layout/Flex";
import { useEntityModal } from "src/components/modalV2";
import EditModal from "src/pages/tenants/modalsV2/EditModal";
import DeleteModal from "src/pages/tenants/modalsV2/DeleteModal";
// TODO: Replace with Tailwind classes or better: remove directly.
import { Description } from "src/pages/tenants/detailV2/components";
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
        <Stack gap="3">
          <Breadcrumbs items={[{ href: "/tenants", title: t("tenants") }]} />
          {loading && !tenant ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {tenant && (
                <Stack gap="3">
                  <Stack orientation="horizontal" gap="1">
                    <PageHeadline>{tenant.name}</PageHeadline>
                    {!isDefaultTenant(tenant.tenantId) && (
                      <OverflowMenu ariaLabel={t("openTenantContextMenu")}>
                        <OverflowMenuItem
                          itemText={t("edit")}
                          onClick={() => editTenant(tenant)}
                        />
                        <OverflowMenuItem
                          itemText={t("delete")}
                          onClick={() => {
                            deleteTenant(tenant);
                          }}
                        />
                      </OverflowMenu>
                    )}
                  </Stack>
                  <p>
                    {t("tenantId")}: {tenant.tenantId}
                  </p>
                  {tenant?.description && (
                    <Description>
                      {t("description")}: {tenant.description}
                    </Description>
                  )}
                </Stack>
              )}
            </Flex>
          )}
        </Stack>
        {tenant && (
          <Section>
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
            />
          </Section>
        )}
      </>
      {editTenantModal}
      {deleteTenantModal}
    </StackPage>
  );
};

export default Details;
