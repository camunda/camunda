/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as fs from 'fs';

export type UserTaskDefinition = {
  readonly id: string;
  readonly name: string;
  readonly assignee?: string;
  readonly isCamundaUserTask: boolean;
};

// Minimal parser tailored to the BPMN test fixtures to avoid extra dependencies.
export function parseUserTasksFromFile(filePath: string): UserTaskDefinition[] {
  const xml = fs.readFileSync(filePath, 'utf-8');
  return parseUserTasksFromXml(xml);
}

export function parseUserTasksFromXml(xml: string): UserTaskDefinition[] {
  const userTaskBlocks =
    xml.match(/<bpmn:userTask\b[^>]*>[\s\S]*?<\/bpmn:userTask>/g) || [];

  return userTaskBlocks.map((block) => {
    const openingTagMatch = block.match(/<bpmn:userTask\b([^>]*)>/);
    const openingTag = openingTagMatch?.[1] ?? '';

    const id = extractAttribute(openingTag, 'id') ?? '';
    const name = extractAttribute(openingTag, 'name') ?? '';
    const assignee = extractAttribute(block, 'assignee');
    const isCamundaUserTask = /<zeebe:userTask\b/.test(block);

    return {id, name, assignee, isCamundaUserTask};
  });
}

function extractAttribute(
  source: string,
  attribute: string,
): string | undefined {
  const match = source.match(new RegExp(`${attribute}="([^"]+)"`));
  return match?.[1];
}
