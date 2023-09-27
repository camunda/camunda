/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import classnames from 'classnames';

import {Labeled} from 'components';
import {t} from 'translation';
import {formatters} from 'services';

import './TenantInfo.scss';

export default function TenantInfo({
  tenant,
  useCarbonVariant,
}: {
  tenant: {id: string; name?: string};
  useCarbonVariant: boolean;
}) {
  return (
    <div className={classnames('TenantInfo', {useCarbonVariant})}>
      {useCarbonVariant ? (
        <div className="cds--label">{t('common.tenant.label')}</div>
      ) : (
        <Labeled label={t('common.tenant.label')} />
      )}
      <p className="tenantName">{formatters.formatTenantName(tenant)}</p>
    </div>
  );
}
