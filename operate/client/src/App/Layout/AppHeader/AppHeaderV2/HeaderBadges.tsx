/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {
  C3LicenseTag,
  useC3Profile,
} from '@camunda/camunda-composite-components';
import {EnvironmentBadge} from '@camunda/design-system';
import {licenseTagStore} from 'modules/stores/licenseTag';
import {getClientConfig} from 'modules/utils/getClientConfig';

const HeaderBadges: React.FC = observer(() => {
  const {clusters} = useC3Profile();
  const {clusterId} = getClientConfig();
  const stage =
    clusters?.find(({uuid}) => uuid === clusterId)?.labels?.camunda?.[0] ?? '';
  const {isTagVisible, isProductionLicense, isCommercial, expiresAt} =
    licenseTagStore.state;

  useEffect(() => {
    licenseTagStore.fetchLicense();
    return licenseTagStore.reset;
  }, []);

  return (
    <div className="flex items-center gap-2">
      <EnvironmentBadge stage={stage} />
      {isTagVisible ? (
        <div className="operate-nav-v2-license-tag">
          <C3LicenseTag
            isProductionLicense={isProductionLicense}
            isCommercial={isCommercial}
            expiresAt={expiresAt ?? undefined}
          />
        </div>
      ) : null}
    </div>
  );
});

export {HeaderBadges};
