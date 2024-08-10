/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FallbackProps} from 'react-error-boundary';
import {useRouteError} from 'react-router-dom';
import {SomethingWentWrong} from 'modules/components/Errors/SomethingWentWrong';
import styles from './styles.module.scss';

const ErrorWithinLayout: React.FC = () => {
  const error = useRouteError();
  console.error(error);

  return <SomethingWentWrong />;
};

const FallbackErrorPage: React.FC<FallbackProps> = ({error}) => {
  console.error(error);
  return (
    <main className={styles.container}>
      <SomethingWentWrong />
    </main>
  );
};

export {ErrorWithinLayout, FallbackErrorPage};
