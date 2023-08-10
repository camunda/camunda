/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';

import {getOptimizeVersion, getOptimizeProfile} from 'config';
import {t} from 'translation';
import {Tooltip} from 'components';

import ConnectionStatus from './ConnectionStatus';

import './Footer.scss';

export default function Footer() {
  const [optimizeVersion, setOptimizeVersion] = useState<string | null>(null);
  const [optimizeProfile, setOptimizeProfile] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      const version = await getOptimizeVersion();
      const profile = await getOptimizeProfile();
      setOptimizeVersion(version);
      setOptimizeProfile(profile);
    }

    fetchData();
  }, []);

  const timezoneInfo =
    t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <footer className="Footer">
      <div className="content">
        {optimizeProfile === 'platform' && <ConnectionStatus />}
        <Tooltip content={timezoneInfo} overflowOnly>
          <div className="timezone">{timezoneInfo}</div>
        </Tooltip>
        <div className="colophon">
          © Camunda Services GmbH {new Date().getFullYear()}, {t('footer.rightsReserved')} |{' '}
          {optimizeVersion}
        </div>
      </div>
    </footer>
  );
}
