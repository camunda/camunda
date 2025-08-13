/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { C3EmptyState } from "@camunda/camunda-composite-components";
import { Add } from "@carbon/react/icons";
import { FC } from "react";
import { documentationHref } from "src/components/documentation";
import { docsUrl } from "src/configuration";
import useTranslate from "src/utility/localization";

type PageEmptyStateProps = {
  resourceType: string;
  docsLinkPath?: string;
  handleClick: () => void;
};

const PageEmptyState: FC<PageEmptyStateProps> = ({
  resourceType,
  docsLinkPath = "",
  handleClick,
}) => {
  const { t } = useTranslate();

  return (
    <C3EmptyState
      heading={t("emptyStateTitleCreate", {
        resourceType,
      })}
      description={t("emptyStateSubtitleCreate", {
        resourceType,
      })}
      button={{
        label: t("emptyStateButtonCreate", {
          resourceType,
        }),
        onClick: handleClick,
        icon: Add,
      }}
      link={{
        href: documentationHref(docsUrl, docsLinkPath),
        label: t("emptyStateLearnTextCreate", {
          resourceType,
        }),
      }}
    />
  );
};

export default PageEmptyState;
