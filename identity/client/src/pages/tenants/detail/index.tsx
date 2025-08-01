/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { useNavigate, useParams } from "react-router";
import { OverflowMenu, OverflowMenuItem, Section, Stack } from "@carbon/react";
import { spacing01, spacing03 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api";
import NotFound from "src/pages/not-found";
import { Breadcrumbs, StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getTenantDetails } from "src/utility/api/tenants";
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";
import { useEntityModal } from "src/components/modal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import { Description } from "src/pages/tenants/detail/components";
import Members from "src/pages/tenants/detail/members";
import Groups from "src/pages/tenants/detail/groups";
import Roles from "src/pages/tenants/detail/roles";
import MappingRules from "src/pages/tenants/detail/mapping-rules";
import Clients from "src/pages/tenants/detail/clients";
import { isOIDC } from "src/configuration";

const Details: FC = () => {
  const { t } = useTranslate("tenants");
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();
  const navigate = useNavigate();
  const { data: tenantSearchResults, loading } = useApi(getTenantDetails, {
    tenantId: id,
  });
  const [deleteTenant, deleteTenantModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  const tenant =
    tenantSearchResults !== null ? tenantSearchResults.items[0] : null;

  if (!loading && !tenant) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap={spacing03}>
          <Breadcrumbs items={[{ href: "/tenants", title: t("tenants") }]} />
          {loading && !tenant ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {tenant && (
                <Stack gap={spacing03}>
                  <Stack orientation="horizontal" gap={spacing01}>
                    <PageHeadline>{tenant.name}</PageHeadline>
                    <OverflowMenu ariaLabel={t("openTenantContextMenu")}>
                      <OverflowMenuItem
                        itemText={t("delete")}
                        onClick={() => {
                          deleteTenant(tenant);
                        }}
                      />
                    </OverflowMenu>
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
                  content: <Members tenantId={tenant.tenantId} />,
                },
                {
                  key: "groups",
                  label: t("groups"),
                  content: <Groups tenantId={tenant.tenantId} />,
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
      {deleteTenantModal}
    </StackPage>
  );
};

export default Details;
