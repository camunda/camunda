/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import useTranslate from "src/utility/localization";
import { useApi } from "src/utility/api/hooks";
import { getMembersByTenantId } from "src/utility/api/membership";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";

type MembersProps = {
  tenantId: string;
};

const Members: FC<MembersProps> = ({ tenantId }) => {
  const { t, Translate } = useTranslate();

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getMembersByTenantId, {
    tenantId: tenantId,
  });

  const areNoUsersAssigned = !users || users.items?.length === 0;
  if (!loading && !success)
    return (
      <C3EmptyState
        heading={t("Something's wrong")}
        description={t(
          'We were unable to load the members. Click "Retry" to try again.',
        )}
        button={{ label: t("Retry"), onClick: reload }}
      />
    );

  if (success && areNoUsersAssigned)
    return (
      <>
        <C3EmptyState
          heading={t("Assign users to this Tenant")}
          description={t(
            "Members of this Tenant will be given access to the data within the Tenant.",
          )}
          link={{
            label: t("Learn more about groups"),
            href: `/identity/concepts/access-control/groups`,
          }}
        />
      </>
    );

  return (
    <>
      <EntityList
        data={users?.items}
        headers={[
          { header: t("Username"), key: "username" },
          { header: t("Name"), key: "name" },
          { header: t("Email"), key: "email" },
        ]}
        sortProperty="username"
        loading={loading}
        addEntityLabel={t("Assign user")}
        searchPlaceholder={t("Search by username")}
      />
      {success && !areNoUsersAssigned && (
        <DocumentationDescription>
          <Translate>To learn more, visit our</Translate>{" "}
          <DocumentationLink path="/concepts/access-control/groups">
            {t("groups documentation")}
          </DocumentationLink>
          .
        </DocumentationDescription>
      )}
      <></>
    </>
  );
};

export default Members;
