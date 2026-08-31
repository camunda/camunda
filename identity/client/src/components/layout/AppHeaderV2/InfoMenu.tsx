/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { HelpCircle } from "lucide-react";
import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from "@camunda/design-system";

import useTranslate from "src/utility/localization";

const INFO_ITEMS = [
  {
    key: "documentation",
    labelKey: "infoDocumentation",
    href: "https://docs.camunda.io/",
  },
  {
    key: "academy",
    labelKey: "infoAcademy",
    href: "https://academy.camunda.com/",
  },
  {
    key: "communityForum",
    labelKey: "infoCommunityForum",
    href: "https://forum.camunda.io",
  },
] as const;

const InfoMenu = () => {
  const { t } = useTranslate("navigation");

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("info")}
        >
          <HelpCircle aria-hidden className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end">
        <DropdownMenuLabel>{t("info")}</DropdownMenuLabel>
        {INFO_ITEMS.map(({ key, labelKey, href }) => (
          <DropdownMenuItem
            key={key}
            onClick={() => window.open(href, "_blank", "noopener,noreferrer")}
          >
            {t(labelKey)}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export { InfoMenu };
