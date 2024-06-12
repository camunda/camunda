/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import { useParams } from "react-router";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import NotFound from "src/pages/not-found";
import { Section } from "@carbon/react";
import { StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getUserDetails } from "src/utility/api/users";
import UserDetails from "./UserDetailsTab";
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";

const Details: FC = () => {
  const { t } = useTranslate();
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();

  const { data: userDetails, loading } = useApi(getUserDetails, {
    id,
  });

  if (!loading && !userDetails) return <NotFound />;

  return (
    <StackPage>
      <>
        {loading && !userDetails ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          <Flex>
            {userDetails && <PageHeadline>{userDetails.username}</PageHeadline>}
          </Flex>
        )}
        <Section>
          <Tabs
            tabs={[
              {
                key: "details",
                label: t("User details"),
                content: userDetails && (
                  <UserDetails user={userDetails} loading={loading} />
                ),
              },
              {
                key: "roles",
                label: t("Assigned roles"),
                content: t("Roles"),
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
