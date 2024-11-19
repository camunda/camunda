/*
 * Copyright © Camunda Services GmbH
 */
import { FC } from "react";
import { useParams } from "react-router";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import NotFound from "src/pages/not-found";
import { Section } from "@carbon/react";
import { StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getTenantDetails } from "src/utility/api/tenants";
import TenantDetailsTab from "./TenantDetailsTab";
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";

const Details: FC = () => {
  const { t } = useTranslate();
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();

  const { data: tenantSearchResults, loading } = useApi(
      getTenantDetails,
      { tenantId: id }
  );

  const tenant =
      tenantSearchResults !== null ? tenantSearchResults.items[0] : null;

  if (!loading && !tenant) return <NotFound />;

  return (
      <StackPage>
        <>
          {loading && !tenant ? (
              <DetailPageHeaderFallback hasOverflowMenu={false} />
          ) : (
              <Flex>
                {tenant && <PageHeadline>{tenant.name}</PageHeadline>}
              </Flex>
          )}
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
                ]}
                selectedTabKey={tab}
                path={`../${id}`}
            />
          </Section>
        </>
      </StackPage>
  );
};

export default Details;
