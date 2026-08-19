/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge} from '@camunda/design-system';
import type {License} from '@camunda/camunda-api-zod-schemas/8.10';
import type {TFunction} from 'i18next';
import {useTranslation} from 'react-i18next';
import {useMemo} from 'react';

const DAY_MS = 1000 * 60 * 60 * 24;
const EXPIRY_WARNING_THRESHOLD_MS = DAY_MS * 30;

type Variant = {
	key: string;
	label: string;
	tone: 'neutral' | 'warning' | 'danger';
	title?: string;
};

function getVariants(license: License, t: TFunction): Variant[] {
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
	const variants = useMemo(() => getVariants(license, t), [license, t]);

	if (license.licenseType === 'saas') {
		return null;
	}

	return (
		<div className="flex items-center gap-2">
			{variants.map(({key, label, tone, title}) => (
				<Badge key={key} variant={tone} title={title}>
					{label}
				</Badge>
			))}
		</div>
	);
};

export {LicenseBadges};
