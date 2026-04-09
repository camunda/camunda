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

export function parseUserTasksFromXml(xml: string): UserTaskDefinition[] {
  const userTaskBlocks = xml.match(
    /<bpmn:userTask\b[^>]*>[\s\S]*?<\/bpmn:userTask>/g,
  );

  if (!userTaskBlocks || userTaskBlocks.length === 0) {
    throw new Error(
      'Expected at least one <bpmn:userTask> element in BPMN XML',
    );
  }

  return userTaskBlocks.map((block, index) => {
    const openingTagMatch = block.match(/<bpmn:userTask\b([^>]*)>/);

    if (!openingTagMatch) {
      throw new Error(
        `Failed to parse opening tag for <bpmn:userTask> at index ${index}`,
      );
    }

    const openingTag = openingTagMatch[1];
    const id = extractAttribute(openingTag, 'id');
    const name = extractAttribute(openingTag, 'name');

    if (!id) {
      throw new Error(
        `Missing required "id" attribute for <bpmn:userTask> at index ${index}`,
      );
    }

    if (!name) {
      throw new Error(
        `Missing required "name" attribute for <bpmn:userTask> with id "${id}"`,
      );
    }
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

/**
 * Parses user tasks from a BPMN file and returns only tasks that have an assignee defined.
 * Throws if any task is missing an assignee, making fixture misconfiguration a hard error.
 */
export function parseAssignedTasksFromFile(
  filePath: string,
): Array<{name: string; assignee: string}> {
  const xml = fs.readFileSync(filePath, 'utf-8');
  return parseUserTasksFromXml(xml).map((task) => {
    if (!task.assignee?.trim()) {
      throw new Error(
        `Expected user task "${task.id}" (${task.name}) in BPMN fixture "${filePath}" to define an assignee`,
      );
    }

    return {name: task.name, assignee: task.assignee};
  });
}
