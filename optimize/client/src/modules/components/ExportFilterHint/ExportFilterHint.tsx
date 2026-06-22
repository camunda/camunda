/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Information} from '@carbon/icons-react';
import {Toggletip, ToggletipActions, ToggletipButton, ToggletipContent} from '@carbon/react';

// imported via its direct path rather than the 'components' barrel to avoid a barrel
// initialization cycle (this component itself lives in that barrel)
import {DocsLink} from 'components/DocsLink';
import {t} from 'translation';

// Path (relative to the docs base resolved by DocsLink) of the exporter docs page that explains
// process- and variable-export filtering. Each variant links to the relevant anchor on that page.
const EXPORTER_DOCS_PAGE =
  'self-managed/components/orchestration-cluster/zeebe/exporters/elasticsearch-exporter/';

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
  const {textKey, docsAnchor} = VARIANTS[variant];

  return (
    <Toggletip className="ExportFilterHint" align="bottom" autoAlign>
      <ToggletipButton label={t('common.exportFilterHint.iconLabel').toString()}>
        <Information />
      </ToggletipButton>
      <ToggletipContent>
        <span>{t(textKey)}</span>
        <ToggletipActions>
          <DocsLink location={EXPORTER_DOCS_PAGE + docsAnchor}>{t('common.seeDocs')}</DocsLink>
        </ToggletipActions>
      </ToggletipContent>
    </Toggletip>
  );
}
