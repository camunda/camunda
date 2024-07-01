/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Information} from '@carbon/icons-react';
import {Toggletip, ToggletipActions, ToggletipButton, ToggletipContent} from '@carbon/react';

import {DocsLink} from 'components';
import {t} from 'translation';

export default function KpiTooltip() {
  return (
    <Toggletip className="KpiTooltip" align="bottom">
      <ToggletipButton>
        <Information />
      </ToggletipButton>
      <ToggletipContent>
        <span>{t('processes.configureKpis')}</span>
        <ToggletipActions>
          <DocsLink location="components/userguide/processes/#set-time-and-quality-kpis">
            {t('common.seeDocs')}
          </DocsLink>
        </ToggletipActions>
      </ToggletipContent>
    </Toggletip>
  );
}
