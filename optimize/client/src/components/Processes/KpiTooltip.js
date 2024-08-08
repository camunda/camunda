/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
