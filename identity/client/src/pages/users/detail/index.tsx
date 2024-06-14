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
import {OverflowMenu, OverflowMenuItem, Section} from "@carbon/react";
import { StackPage } from "src/components/layout/Page";
import PageHeadline from "src/components/layout/PageHeadline";
import { getUserDetails } from "src/utility/api/users";
import UserDetails from "./UserDetailsTab";
import Tabs from "src/components/tabs";
import { DetailPageHeaderFallback } from "src/components/fallbacks";
import Flex from "src/components/layout/Flex";
import {useEntityModal} from "src/components/modal";
import EditModal from "src/pages/users/EditModal";

const Details: FC = () => {
  const { t } = useTranslate();
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();
  const {
    data: user,
    loading,
    reload,
  } = useApi(getUserDetails, {
    id,
  });
  const [editUser, editUserModal] = useEntityModal(EditModal, reload);

  if (!loading && !user) return <NotFound/>;

  return (
    <StackPage>
      <>
        {loading && !user ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          <Flex>
            {user && (
                <>
                  <PageHeadline>{user.username}</PageHeadline>
                  <OverflowMenu ariaLabel={t("Open users context menu")}>
                    <OverflowMenuItem
                        itemText={t("Update")}
                        onClick={() => {
                          editUser(user);
                        }}
                    />
                  </OverflowMenu>
                </>
            )}
          </Flex>
        )}
        <Section>
          <Tabs
            tabs={[
              {
                key: "details",
                label: t("User details"),
                content: user && <UserDetails user={user} loading={loading}/>,
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
      {editUserModal}
    </StackPage>
  );
};

export default Details;
