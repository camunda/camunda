/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AutoFocusPlugin} from '@lexical/react/LexicalAutoFocusPlugin';
import {CheckListPlugin} from '@lexical/react/LexicalCheckListPlugin';
import {HistoryPlugin} from '@lexical/react/LexicalHistoryPlugin';
import {ListPlugin} from '@lexical/react/LexicalListPlugin';
import HorizontalRulePlugin from './HorizontalRulePlugin';

import ImagesPlugin from './ImagesPlugin';
import LinkPlugin from './LinkPlugin';

export {ToolbarPlugin} from './ToolbarPlugin';

const editorPlugins = [
  HistoryPlugin,
  HorizontalRulePlugin,
  ImagesPlugin,
  ListPlugin,
  LinkPlugin,
  CheckListPlugin,
  AutoFocusPlugin,
];

export default editorPlugins;
