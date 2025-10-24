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
  resourceTypeTranslationKey: string;
  docsLinkPath?: string;
  handleClick: () => void;
};

const PageEmptyState: FC<PageEmptyStateProps> = ({
  resourceTypeTranslationKey,
  docsLinkPath = "",
  handleClick,
}) => {
  const { t } = useTranslate();

  const resourceTypeText = t(resourceTypeTranslationKey).toLowerCase();

  return (
    <C3EmptyState
      heading={t("emptyStateTitleCreate", {
        resourceType: resourceTypeText,
      })}
      description={t("emptyStateSubtitleCreate", {
        resourceType: resourceTypeText,
      })}
      button={{
        label: t("emptyStateButtonCreate", {
          resourceType: resourceTypeText,
        }),
        onClick: handleClick,
        icon: Add,
      }}
      link={{
        href: documentationHref(docsUrl, docsLinkPath),
        label: t("emptyStateLearnText", {
          resourceType: resourceTypeText,
        }),
      }}
    />
  );
};

export default PageEmptyState;
