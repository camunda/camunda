/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useForm} from 'react-final-form';

type Props = {
  isAssigned: boolean;
};

const ResetForm: React.FC<Props> = ({isAssigned}) => {
  const form = useForm();

  useEffect(() => {
    if (!form.getState().submitting) {
      form.reset();
    }
  }, [isAssigned, form]);

  return null;
};

export {ResetForm};
