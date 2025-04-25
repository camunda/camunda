/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Tag} from '@carbon/react';
import {BPMNDiagram} from 'common/bpmn-js/BPMNDiagram';
import {SomethingWentWrong} from 'common/error-handling/SomethingWentWrong';
import styles from './index.module.scss';
import {useTranslation} from 'react-i18next';

type Props = {
  status: 'error' | 'diagram';
  xml: string;
  elementId: string;
  processName: string;
  processVersion: number;
};

const ProcessDiagramView: React.FC<Props> = ({
  status,
  xml,
  elementId,
  processName,
  processVersion,
}) => {
  const {t} = useTranslation();

  if (status === 'error') {
    return <SomethingWentWrong className={styles.somethingWentWrong} />;
  }

  return (
    <Layer className={styles.container}>
      <div className={styles.header}>
        <span className={styles.processName}>{processName}</span>
        <Tag className={styles.version}>
          {t('processViewProcessVersion', {version: processVersion})}
        </Tag>
      </div>
      <Layer className={styles.diagramFrame}>
        <BPMNDiagram xml={xml} highlightActivity={[elementId]} />
      </Layer>
    </Layer>
  );
};

export {ProcessDiagramView};
