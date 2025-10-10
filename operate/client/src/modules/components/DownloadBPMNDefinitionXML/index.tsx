/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTransition} from 'react';
import {IconButton, Loading} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import {getUseProcessDefinitionXmlOptions} from 'modules/queries/processDefinitions/useProcessDefinitionXml';
import {getUseProcessDefinitionOptions} from 'modules/queries/processDefinitions/useProcessDefinition';
import {getDiagramNameByProcessDefinition} from 'modules/utils/getDiagramNameByProcessDefinition';
import {useQueryClient} from '@tanstack/react-query';

type Props = {
  processDefinitionKey: string;
  className?: string;
};

const DownloadBPMNDefinitionXML: React.FC<Props> = ({
  processDefinitionKey,
  className,
}) => {
  const queryClient = useQueryClient();
  const [isPending, startTransition] = useTransition();

  const handleDownload = async () => {
    try {
      const [{xml, diagramModel}, processDefinition] = await Promise.all([
        queryClient.ensureQueryData(
          getUseProcessDefinitionXmlOptions(processDefinitionKey),
        ),
        queryClient.ensureQueryData(
          getUseProcessDefinitionOptions(processDefinitionKey),
        ),
      ]);

      const diagramName =
        processDefinition === undefined
          ? diagramModel.rootElement.id
          : getDiagramNameByProcessDefinition(processDefinition);

      const blob = new Blob([xml], {type: 'application/xml'});
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${diagramName}.bpmn`;
      document.body.appendChild(link);
      link.click();

      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (downloadError) {
      console.error('Failed to download BPMN definition:', downloadError);
    }
  };

  return (
    <IconButton
      kind="tertiary"
      size="sm"
      align="left"
      disabled={isPending}
      onClick={() =>
        startTransition(async () => {
          await handleDownload();
        })
      }
      className={className}
      label="Download XML"
      aria-label="Download BPMN definition XML"
    >
      {isPending ? <Loading withOverlay={false} small /> : <Download />}
    </IconButton>
  );
};

export {DownloadBPMNDefinitionXML};
