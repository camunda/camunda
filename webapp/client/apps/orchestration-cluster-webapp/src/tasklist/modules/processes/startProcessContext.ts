/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext} from 'react';
import type {ActorRefFrom} from 'xstate';
import type {startProcessMachine} from './startProcessMachine';

type StartProcessActor = ActorRefFrom<typeof startProcessMachine>;

const StartProcessContext = createContext<StartProcessActor | null>(null);

export {StartProcessContext};
