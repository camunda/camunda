/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C3LicenseTag, useC3Profile} from '@camunda/camunda-composite-components';
import {EnvironmentBadge} from '@camunda/design-system';

import {useUiConfig} from 'hooks';

export default function HeaderBadges({hideLicense}: {hideLicense?: boolean}) {
  const {clusters} = useC3Profile();
  const {onboarding, validLicense, licenseType, commercial, expiresAt} = useUiConfig();
  const stage =
    clusters?.find(({uuid}) => uuid === onboarding.clusterId)?.labels?.camunda?.[0] ?? '';
  const showLicenseTag = !hideLicense && licenseType !== 'saas';

  return (
    <div className="flex items-center gap-2">
      <EnvironmentBadge stage={stage} />
      {showLicenseTag ? (
        <div className="HeaderV2-license-tag flex items-center">
          <C3LicenseTag
            isProductionLicense={validLicense}
            isCommercial={commercial}
            expiresAt={expiresAt ?? undefined}
          />
        </div>
      ) : null}
    </div>
  );
}
