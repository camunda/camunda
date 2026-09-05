/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { Button, PageHeader } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import { userQueries } from "src/utility/api/users/queries";
import NotFound from "src/pages/not-foundV2";
import { Breadcrumbs, StackPage } from "src/components/layoutV2/Page";
import UserDetails from "./UserDetailsTab";
import { Tabs, TabsHeaderList, TabsContent } from "src/components/tabsV2";
import { DetailPageHeaderFallback } from "src/components/fallbacksV2";
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
  const breadcrumbs = useMemo(() => {
    if (!user) return [];

    return [{ title: t("users"), href: ".." }, { title: user.username }];
  }, [user, t]);
  const tabs = useMemo(() => {
    if (!user) return [];

    return [
      {
        key: "details",
        label: t("userDetails"),
        content: <UserDetails user={user} loading={loading} />,
      },
    ];
  }, [user, loading, t]);

  if (!loading && !user) return <NotFound />;

  return (
    <StackPage>
      <>
        {loading && !user ? (
          <DetailPageHeaderFallback hasOverflowMenu={false} />
        ) : (
          user && (
            <Tabs path={`../${id}`} tabs={tabs} selectedTabKey={tab}>
              <PageHeader
                title={user.username}
                breadcrumb={<Breadcrumbs items={breadcrumbs} />}
                actions={
                  <>
                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      onClick={() => editUser(user)}
                    >
                      <Pencil aria-hidden="true" />
                      {t("update")}
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={() => deleteUser(user)}
                    >
                      <Trash2 aria-hidden="true" />
                      {t("delete")}
                    </Button>
                  </>
                }
                tabs={<TabsHeaderList />}
              />
              <TabsContent />
            </Tabs>
          )
        )}
      </>
      {editUserModal}
      {deleteUserModal}
    </StackPage>
  );
};

export default Details;
