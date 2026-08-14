/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import type {License} from '@camunda/camunda-api-zod-schemas/8.10';

const DAY_MS = 1000 * 60 * 60 * 24;
const EXPIRY_WARNING_THRESHOLD_MS = DAY_MS * 30;

type Variant = {
	key: string;
	label: string;
	tone: 'neutral' | 'warning' | 'danger';
	title?: string;
};

// DS-only. Mirrors @camunda/camunda-composite-components' C3LicenseTag: a
// production-status tag is always shown, plus a non-commercial tag (or its
// expiring/expired variant) when isCommercial is false. C3LicenseTag itself
// renders Carbon Tag/Toggletip, which would sit as a foreign surface inside
// the DS header, so this reproduces the same tag/variant rules on DS Badge
// instead. The explanatory copy that C3's Toggletip shows on hover is carried
// as a native `title` attribute rather than a DS Tooltip, to keep this a
// narrow port rather than a new interactive component.
function getVariants(license: License, t: (key: string, options?: Record<string, unknown>) => string): Variant[] {
	const variants: Variant[] = [
		{
			key: 'license-status',
			label: license.validLicense ? t('tasklist.headerProductionLicenseLabel') : t('headerNonProductionLicenseLabel'),
			tone: 'neutral',
			title: license.validLicense ? undefined : t('tasklist.headerNonProductionLicenseTitle'),
		},
	];

	if (!license.isCommercial) {
		const expiresAt = license.expiresAt === null ? undefined : Date.parse(license.expiresAt);
		const now = Date.now();

		if (expiresAt !== undefined && expiresAt < now) {
			variants.push({
				key: 'non-commercial-expired',
				label: t('tasklist.headerNonCommercialLicenseExpiredLabel'),
				tone: 'danger',
				title: t('tasklist.headerNonCommercialLicenseExpiredTitle'),
			});
		} else if (expiresAt !== undefined && expiresAt - EXPIRY_WARNING_THRESHOLD_MS < now) {
			const daysLeft = Math.max(0, Math.floor((expiresAt - now) / DAY_MS));
			variants.push({
				key: 'non-commercial-expiring',
				label: t('tasklist.headerNonCommercialLicenseExpiringLabel', {count: daysLeft}),
				tone: 'warning',
				title: t('tasklist.headerNonCommercialLicenseExpiringTitle'),
			});
		} else {
			variants.push({
				key: 'non-commercial',
				label: t('tasklist.headerNonCommercialLicenseLabel'),
				tone: 'neutral',
			});
		}
	}

	return variants;
}

type Props = {
	license: License;
};

const LicenseBadges: React.FC<Props> = ({license}) => {
	const {t} = useTranslation();

	// Matches C3Navigation's own licenseTag.show rule: hidden entirely on SaaS,
	// where license status isn't a self-managed customer's concern.
	if (license.licenseType === 'saas') {
		return null;
	}

	return (
		<div className="flex items-center gap-2">
			{getVariants(license, t).map(({key, label, tone, title}) => (
				<Badge key={key} variant={tone} title={title}>
					{label}
				</Badge>
			))}
		</div>
	);
};

export {LicenseBadges};
