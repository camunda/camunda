/*
 * Copyright © Camunda Services GmbH
 */
import { FC } from "react";
import { useNavigate, useParams } from "react-router";
import { OverflowMenu, OverflowMenuItem, Section, Stack } from "@carbon/react";
import { spacing02 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import NotFound from "src/pages/not-found";
import { Breadcrumbs, StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getTenantDetails } from "src/utility/api/tenants";
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";
import { useEntityModal } from "src/components/modal";
import EditModal from "src/pages/tenants/modals/EditModal";
import DeleteModal from "src/pages/tenants/modals/DeleteModal";
import TenantDetailsTab from "src/pages/tenants/detail/TenantDetailsTab.tsx";
import Members from "src/pages/tenants/detail/members";

const Details: FC = () => {
  const { t } = useTranslate();
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();
  const navigate = useNavigate();
  const {
    data: tenantSearchResults,
    loading,
    reload,
  } = useApi(getTenantDetails, {
    tenantId: id,
  });
  const [editTenant, editTenantModal] = useEntityModal(EditModal, reload);
  const [deleteTenant, deleteTenantModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );

  const tenant =
    tenantSearchResults !== null ? tenantSearchResults.items[0] : null;

  if (!loading && !tenant) return <NotFound />;

  return (
    <StackPage>
      <>
        <Stack gap={spacing02}>
          <Breadcrumbs items={[{ href: "/tenants", title: t("Tenants") }]} />
          {loading && !tenant ? (
            <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
            <Flex>
              {tenant && (
                <>
                  {" "}
                  <PageHeadline>{tenant.name}</PageHeadline>
                  <OverflowMenu ariaLabel={t("Open users context menu")}>
                    <OverflowMenuItem
                      itemText={t("Update")}
                      onClick={() => {
                        editTenant(tenant);
                      }}
                    />
                    <OverflowMenuItem
                      itemText={t("Delete")}
                      onClick={() => {
                        deleteTenant(tenant);
                      }}
                    />
                  </OverflowMenu>
                </>
              )}
            </Flex>
          )}
        </Stack>
        {tenant && (
          <Section>
            <Tabs
              tabs={[
                {
                  key: "details",
                  label: t("Tenant details"),
                  content: tenant && (
                    <TenantDetailsTab tenant={tenant} loading={loading} />
                  ),
                },
                {
                  key: "users",
                  label: t("Users"),
                  content: <Members tenantId={tenant.tenantId} />,
                },
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
