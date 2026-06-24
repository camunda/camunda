/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Information} from '@carbon/icons-react';
import {Toggletip, ToggletipActions, ToggletipButton, ToggletipContent} from '@carbon/react';

// DocsLink is imported relatively (not via the 'components' barrel) — see this folder's index.tsx.
import {DocsLink} from '../DocsLink';
import {useUiConfig} from 'hooks';
import {t} from 'translation';

// The exporter docs page depends on which store the cluster exports to. We use Optimize's own
// database as the signal (the Zeebe exporter writes to the same store Optimize reads from). Both
// the Elasticsearch and OpenSearch exporter pages expose the same filter anchors.
const exporterDocsPage = (database: 'opensearch' | 'elasticsearch') =>
  `self-managed/components/orchestration-cluster/zeebe/exporters/${database}-exporter/`;

// The contexts the hint is shown in. Each maps to its own explanation and docs anchor:
// - variable: variable filters (variable-name export filtering)
// - reportSetup: report setup, where whole processes can also be excluded
const VARIANTS = {
  variable: {textKey: 'common.exportFilterHint.variableText', docsAnchor: '#variable-name-filters'},
  reportSetup: {
    textKey: 'common.exportFilterHint.reportSetupText',
    docsAnchor: '#bpmn-process-filters',
  },
} as const;

interface ExportFilterHintProps {
  variant: keyof typeof VARIANTS;
}

export default function ExportFilterHint({variant}: ExportFilterHintProps): JSX.Element {
  const {optimizeDatabase} = useUiConfig();
  const {textKey, docsAnchor} = VARIANTS[variant];

  return (
    <Toggletip className="ExportFilterHint" align="bottom" autoAlign>
      <ToggletipButton label={t('common.exportFilterHint.iconLabel').toString()}>
        <Information />
      </ToggletipButton>
      <ToggletipContent>
        <span>{t(textKey)}</span>
        <ToggletipActions>
          <DocsLink location={exporterDocsPage(optimizeDatabase) + docsAnchor}>
            {t('common.seeDocs')}
          </DocsLink>
        </ToggletipActions>
      </ToggletipContent>
    </Toggletip>
  );
}
