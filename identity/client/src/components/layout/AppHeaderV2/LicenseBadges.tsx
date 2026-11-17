/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Badge,
  Link,
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverTrigger,
} from "@camunda/design-system";
import type { License } from "@camunda/camunda-api-zod-schemas/8.10";
import type { TFunction } from "i18next";
import type { ReactNode } from "react";

import useTranslate from "src/utility/localization";

const DAY_MS = 1000 * 60 * 60 * 24;
const EXPIRY_WARNING_THRESHOLD_MS = DAY_MS * 30;

const NON_PRODUCTION_TERMS_LINK =
  "https://legal.camunda.com/#self-managed-non-production-terms";
const SALES_CONTACT_LINK = "https://camunda.com/contact/";

const UNKNOWN_LICENSE: License = {
  validLicense: false,
  licenseType: "unknown",
  isCommercial: false,
  expiresAt: null,
};

type Variant = {
  key: string;
  label: string;
  tone: "neutral" | "warning" | "danger";
  description?: ReactNode;
};

function getVariants(
  license: License,
  t: TFunction,
  nonProductionDescription: ReactNode,
): Variant[] {
  const variants: Variant[] = [
    {
      key: "license-status",
      label: license.validLicense
        ? t("licenseProductionLabel")
        : t("licenseNonProductionLabel"),
      tone: "neutral",
      description: license.validLicense ? undefined : nonProductionDescription,
    },
  ];
  if (license.isCommercial) {
    return variants;
  }

  const expiresAt =
    license.expiresAt === null ? NaN : Date.parse(license.expiresAt);
  const now = Date.now();

  if (!Number.isNaN(expiresAt) && expiresAt < now) {
    variants.push({
      key: "non-commercial-expired",
      label: t("licenseNonCommercialExpiredLabel"),
      tone: "danger",
      description: t("licenseNonCommercialExpiredDescription"),
    });
  } else if (
    !Number.isNaN(expiresAt) &&
    expiresAt - EXPIRY_WARNING_THRESHOLD_MS < now
  ) {
    const daysLeft = Math.max(0, Math.floor((expiresAt - now) / DAY_MS));
    variants.push({
      key: "non-commercial-expiring",
      label: t("licenseNonCommercialExpiringLabel", { count: daysLeft }),
      tone: "warning",
      description: t("licenseNonCommercialExpiringDescription"),
    });
  } else {
    variants.push({
      key: "non-commercial",
      label: t("licenseNonCommercialLabel"),
      tone: "neutral",
    });
  }

  return variants;
}

type Props = {
  license: License | null | undefined;
};

export const LicenseBadges = ({ license }: Props) => {
  const { t, Translate } = useTranslate("navigation");
  const resolvedLicense = license ?? UNKNOWN_LICENSE;
  const nonProductionDescription = (
    <Translate
      i18nKey="licenseNonProductionDescription"
      components={{
        termsLink: (
          <Link
            href={NON_PRODUCTION_TERMS_LINK}
            target="_blank"
            rel="noreferrer noopener"
            inline
          />
        ),
        salesLink: (
          <Link
            href={SALES_CONTACT_LINK}
            target="_blank"
            rel="noreferrer noopener"
            inline
          />
        ),
      }}
    />
  );
  const variants = getVariants(resolvedLicense, t, nonProductionDescription);

  if (resolvedLicense.licenseType === "saas") {
    return null;
  }

  return (
    <div className="flex items-center gap-2">
      {variants.map(({ key, label, tone, description }) =>
        description === undefined ? (
          <Badge key={key} variant={tone}>
            {label}
          </Badge>
        ) : (
          <Popover key={key}>
            <PopoverTrigger asChild>
              <Badge asChild variant={tone}>
                <button type="button">{label}</button>
              </Badge>
            </PopoverTrigger>
            <PopoverContent align="center">
              <PopoverDescription>{description}</PopoverDescription>
            </PopoverContent>
          </Popover>
        ),
      )}
    </div>
  );
};
