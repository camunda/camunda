import { json } from '@remix-run/react';

export * from '@camunda/tasklist/routes/noTaskSelected';
export {default} from '@camunda/tasklist/routes/noTaskSelected';
export function clientLoader() {
  return json({});
}
