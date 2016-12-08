import {jsx} from 'view-utils';

export function createMockComponent(text) {
  return () => <div>{text}</div>;
}
