/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useNavigate, useOutletContext} from 'react-router-dom';
import {Tag} from '@carbon/react';
import {pages} from 'modules/routing';
import {BPMNDiagram} from 'modules/components/BPMNDiagram';
import {OutletContext} from '../Details';
import styles from './index.module.scss';

const ProcessView: React.FC = () => {
  const navigate = useNavigate();
  const {task, process} = useOutletContext<OutletContext>();

  useEffect(() => {
    if (process === undefined || process.bpmnProcessId === undefined) {
      navigate(pages.taskDetails(task.id));
    }
  }, [navigate, process, task.id]);

  if (process === undefined || process.bpmnProcessId === undefined) {
    return null;
  }

  const {name, version, bpmnXml} = process;
  const {taskDefinitionId} = task;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.processName}>{name}</span>
        <Tag className={styles.version}>Version: {version}</Tag>
      </div>
      <div className={styles.diagramFrame}>
        <BPMNDiagram
          xml={bpmnXml ?? ''}
          highlightActivity={[taskDefinitionId]}
        />
      </div>
    </div>
  );
};

export {ProcessView as Component};
