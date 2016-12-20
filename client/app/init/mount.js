import {jsx, runUpdate, createEventsBus} from 'view-utils';

export function mountMain(Main) {
  const template = <Main />;
  const eventsBus = createEventsBus();

  return runUpdate.bind(
    null,
    template(document.body, eventsBus)
  );
}
