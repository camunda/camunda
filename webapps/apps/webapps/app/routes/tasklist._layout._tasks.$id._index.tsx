import { json } from '@remix-run/react';

export * from '@camunda/tasklist/routes/taskDetailsIndex';
export {default} from '@camunda/tasklist/routes/taskDetailsIndex';
export function clientLoader() {
  return json({});
}