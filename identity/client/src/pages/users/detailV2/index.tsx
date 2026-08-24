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
import useTranslate from "src/utility/localization";
import { userQueries } from "src/utility/api/users/queries";
import NotFound from "src/pages/not-foundV2";
import { OverflowMenu, OverflowMenuItem, Section } from "@carbon/react";
import { StackPage } from "src/components/layoutV2/Page";
import PageHeadline from "src/components/layoutV2/PageHeadline";
import UserDetails from "./UserDetailsTab";
import Tabs from "src/components/tabsV2";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
// TODO: Replace with Tailwind classes
import Flex from "src/components/layout/Flex";
import { useEntityModal } from "src/components/modalV2";
import EditModal from "src/pages/users/modalsV2/EditModal";
import DeleteModal from "src/pages/users/modalsV2/DeleteModal";

const Details: FC = () => {
  const { t } = useTranslate("users");
  const { id = "", tab = "details" } = useParams<{ id: string; tab: string }>();
  const navigate = useNavigate();
  const { data: userSearchResults, isLoading: loading } = useQuery(
    userQueries.detail(id),
  );
  const [editUser, editUserModal] = useEntityModal(EditModal, () => {});
  const [deleteUser, deleteUserModal] = useEntityModal(DeleteModal, () =>
    navigate("..", { replace: true }),
  );
  const user = userSearchResults ? userSearchResults.items[0] : null;
  if (!loading && !user) return <NotFound />;

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
                <OverflowMenu ariaLabel={t("openContextMenu")}>
                  <OverflowMenuItem
                    itemText={t("update")}
                    onClick={() => {
                      editUser(user);
                    }}
                  />
                  <OverflowMenuItem
                    itemText={t("delete")}
                    onClick={() => {
                      deleteUser(user);
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
                label: t("userDetails"),
                content: user && <UserDetails user={user} loading={loading} />,
              },
            ]}
            selectedTabKey={tab}
            path={`../${id}`}
          />
        </Section>
      </>
      {editUserModal}
      {deleteUserModal}
    </StackPage>
  );
};

export default Details;
