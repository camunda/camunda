/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {$isListNode, ListNode} from '@lexical/list';
import {$isHeadingNode} from '@lexical/rich-text';
import {$getNearestNodeOfType} from '@lexical/utils';
import {ElementNode, LexicalNode, TextNode} from 'lexical';

import {BLOCK_TYPES} from './BlockTypeOptions';

export function getNodeType(element: LexicalNode, anchorNode: TextNode | ElementNode) {
  if ($isListNode(element)) {
    const parentList = $getNearestNodeOfType(anchorNode, ListNode);
    return parentList ? parentList.getListType() : element.getListType();
  } else {
    const type = $isHeadingNode(element) ? element.getTag() : element.getType();
    return BLOCK_TYPES.some((blockType) => blockType === type) ? type : 'paragraph';
  }
}
