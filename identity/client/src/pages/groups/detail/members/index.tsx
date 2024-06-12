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
import { getGroupUsers } from "src/utility/api/groups";
import EntityList, {
  DocumentationDescription,
} from "src/components/entityList";
import { DocumentationLink } from "src/components/documentation";
import { TrashCan } from "@carbon/react/icons";

type MembersProps = {
  groupId: string;
};

const Members: FC<MembersProps> = ({ groupId }) => {
  const { t, Translate } = useTranslate();

  const {
    data: users,
    loading,
    success,
    reload,
  } = useApi(getGroupUsers, {
    id: groupId,
  });

  const areNoUsersAssigned = !users || users.length === 0;

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
          heading={t("Assign members to this group")}
          description={t(
            "Members of this group will be given access and roles that are assigned to this group.",
          )}
          button={{
            label: t("Assign members"),
          }}
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
        title={t("Assigned members")}
        data={users}
        headers={[
          { header: t("Full name"), key: "name" },
          { header: t("Username"), key: "username" },
          { header: t("Email"), key: "email" },
        ]}
        sortProperty="name"
        loading={loading}
        addEntityLabel={t("Assign members")}
        menuItems={[
          {
            label: t("Remove"),
            icon: TrashCan,
            isDangerous: true,
            onClick: () => {},
          },
        ]}
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
    </>
  );
};

export default Members;
