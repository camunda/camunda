/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback} from 'react';
import {IconButton} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import {useProcessDefinitionXml} from 'modules/queries/processDefinitions/useProcessDefinitionXml';
import {useProcessDefinition} from 'modules/queries/processDefinitions/useProcessDefinition';
import {getDiagramNameByProcessDefinition} from 'modules/utils/processDefinition';
import _ from 'lodash';

interface DownloadBPMNDefinitionXMLProps {
  processDefinitionKey: string | undefined;
  disabled?: boolean;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  kind?: 'primary' | 'secondary' | 'tertiary' | 'ghost';
  label?: string;
  ariaLabel?: string;
  align?:
    | 'left'
    | 'right'
    | 'top'
    | 'top-left'
    | 'top-right'
    | 'bottom'
    | 'bottom-left'
    | 'bottom-right';
}

const DownloadBPMNDefinitionXML: React.FC<DownloadBPMNDefinitionXMLProps> = ({
  processDefinitionKey,
  disabled = false,
  className,
  size = 'sm',
  align = 'left',
  kind = 'tertiary',
  label = 'Download XML',
  ariaLabel = 'Download BPMN definition XML',
}) => {
  const {isLoading, data: xmlData} = useProcessDefinitionXml({
    processDefinitionKey,
    select: (data) => data.xml,
  });

  const {data: processDefinition} = useProcessDefinition(processDefinitionKey);

  const handleDownload = useCallback(async () => {
    try {
      if (!xmlData) {
        console.error('No BPMN XML data available for download');
        return;
      }

      const blob = new Blob([xmlData], {type: 'application/xml'});
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;

      link.download = `${_.kebabCase(
        processDefinition
          ? getDiagramNameByProcessDefinition(processDefinition)
          : 'diagram',
      )}.bpmn`;
      document.body.appendChild(link);
      link.click();

      // Cleanup
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      console.error('Failed to download BPMN definition:', downloadError);
    }
  }, [xmlData, processDefinition]);

  return (
    <IconButton
      kind={kind}
      size={size}
      align={align}
      disabled={disabled || isLoading || !xmlData}
      onClick={handleDownload}
      className={className}
      label={label}
      aria-label={ariaLabel}
    >
      <Download />
    </IconButton>
  );
};

export {DownloadBPMNDefinitionXML};
