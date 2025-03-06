/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useOutletContext} from 'react-router-dom';
import {Layer, Tag} from '@carbon/react';
import {BPMNDiagram} from 'common/bpmn-js/BPMNDiagram';
import {SomethingWentWrong} from 'common/error-handling/SomethingWentWrong';
import type {OutletContext} from '../Details';
import styles from './index.module.scss';
import {useTranslation} from 'react-i18next';

const ProcessView: React.FC = () => {
  const {task, process} = useOutletContext<OutletContext>();
  const {t} = useTranslation();

  if (process === undefined) {
    return <SomethingWentWrong className={styles.somethingWentWrong} />;
  }

  const {name, version, bpmnXml} = process;
  const {taskDefinitionId} = task;

  return (
    <Layer className={styles.container}>
      <div className={styles.header}>
        <span className={styles.processName}>{name}</span>
        <Tag className={styles.version}>
          {t('processViewProcessVersion', {version})}
        </Tag>
      </div>
      {bpmnXml !== null ? (
        <Layer className={styles.diagramFrame}>
          <BPMNDiagram xml={bpmnXml} highlightActivity={[taskDefinitionId]} />
        </Layer>
      ) : null}
    </Layer>
  );
};

export {ProcessView as Component};
