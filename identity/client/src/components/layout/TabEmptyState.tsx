/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Add } from "@carbon/react/icons";
import { docsUrl } from "src/configuration";
import { documentationHref } from "src/components/documentation";
import useTranslate from "src/utility/localization";

type TabEmptyStateProps = {
  childResourceType: string;
  parentResourceType: string;
  docsLinkPath?: string;
  handleClick: () => void;
};

const TabEmptyState: FC<TabEmptyStateProps> = ({
  childResourceType,
  parentResourceType,
  docsLinkPath = "",
  handleClick,
}) => {
  const { t } = useTranslate();

  return (
    <C3EmptyState
      heading={t("emptyStateTitleAssign", {
        childResourceType,
        parentResourceType,
      })}
      description={
        parentResourceType === t("tenant").toLowerCase()
          ? t("emptyStateTenantAccessDisclaimer")
          : t("emptyStateSubtitleAssign", {
              childResourceType,
            })
      }
      button={{
        label: t("emptyStateButtonAssign", {
          childResourceType,
        }),
        onClick: handleClick,
        icon: Add,
      }}
      link={{
        href: documentationHref(docsUrl, docsLinkPath),
        label: t("emptyStateLearnText", {
          resourceType: childResourceType,
        }),
      }}
    />
  );
};

export default TabEmptyState;
