/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Button, EmptyState } from "@camunda/design-system";
import { Plus } from "lucide-react";
import { useDocsUrl } from "../documentation/DocsUrlContext";
import { documentationHref } from "src/components/documentationV2";
import useTranslate from "src/utility/localization";

type TabEmptyStateProps = {
  childResourceTypeTranslationKey: string;
  parentResourceTypeTranslationKey: string;
  description?: string;
  docsLinkPath?: string;
  handleClick: () => void;
};

const TabEmptyState: FC<TabEmptyStateProps> = ({
  childResourceTypeTranslationKey,
  parentResourceTypeTranslationKey,
  description,
  docsLinkPath = "",
  handleClick,
}) => {
  const { t } = useTranslate();
  const docsUrl = useDocsUrl();

  const childResourceTypeText = t(
    childResourceTypeTranslationKey,
  ).toLowerCase();
  const parentResourceTypeText = t(
    parentResourceTypeTranslationKey,
  ).toLowerCase();

  return (
    <EmptyState
      heading={t("emptyStateTitleAssign", {
        childResourceType: childResourceTypeText,
        parentResourceType: parentResourceTypeText,
      })}
      description={
        description
          ? description
          : t("emptyStateSubtitleAssign", {
              childResourceType: childResourceTypeText,
            })
      }
      action={
        <Button onClick={handleClick}>
          <Plus data-icon="inline-start" aria-hidden="true" />
          {t("emptyStateButtonAssign", {
            childResourceType: childResourceTypeText,
          })}
        </Button>
      }
      secondaryAction={
        <Button variant="link" size="sm" asChild>
          <a
            href={documentationHref(docsUrl, docsLinkPath)}
            target="_blank"
            rel="noreferrer noopener"
          >
            {t("emptyStateLearnText", {
              resourceType: childResourceTypeText,
            })}
          </a>
        </Button>
      }
    />
  );
};

export default TabEmptyState;
