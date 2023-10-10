/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';
import {useDocs} from 'hooks';

import TemplateModal from './TemplateModal';
import {
  accelerationDashboardTemplate,
  efficiencyDashboardTemplate,
  operationsMonitoringDashboardTemplate,
  portfolioPerformanceDashboardTemplate,
  processDashboardTemplate,
  productivityDashboardTemplate,
} from './templates';

export default function DashboardTemplateModal({
  onClose,
  onConfirm,
  initialDefinitions,
  trackingEventName,
}) {
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [optimizeProfileLoaded, setOptimizeProfileLoaded] = useState(false);
  const {docsLink} = useDocs();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
      setOptimizeProfileLoaded(true);
    })();
  }, []);

  let templateGroups = [
    {
      name: 'blankGroup',
      templates: [{name: 'blank', disableDescription: true}],
    },
    {
      name: 'singleProcessGroup',
      templates: [
        processDashboardTemplate(docsLink),
        productivityDashboardTemplate(docsLink),
        efficiencyDashboardTemplate(docsLink),
        accelerationDashboardTemplate(docsLink),
      ],
    },
    {
      name: 'multiProcessGroup',
      templates: [operationsMonitoringDashboardTemplate()],
    },
  ];

  if (optimizeProfile === 'platform') {
    templateGroups[2].templates.unshift(portfolioPerformanceDashboardTemplate());
  }

  if (!optimizeProfileLoaded) {
    return null;
  }

  return (
    <TemplateModal
      initialDefinitions={initialDefinitions}
      onConfirm={onConfirm}
      onClose={onClose}
      templateGroups={templateGroups}
      entity="dashboard"
      trackingEventName={trackingEventName}
      blankSlate={
        <ol>
          <li>{t('templates.blankSlate.selectProcess')}</li>
          <li>{t('templates.blankSlate.selectTemplate')}</li>
          <li>{t('templates.blankSlate.review')}</li>
          <li>{t('templates.blankSlate.refine')}</li>
        </ol>
      }
      templateToState={({template, ...props}) => ({
        ...props,
        data: template || [],
      })}
    />
  );
}
