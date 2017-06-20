import {
  jsx, Text, OnEvent, createReferenceComponent, Match, Case,
  addClass, updateOnlyWhenStateChanges
} from 'view-utils';
import {removeNotification} from './service';

export function Notification() {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();
    const template = <div className="alert" role="alert">
      <Reference name="alert" />
      <button type="button" className="close" aria-label="close">
        <OnEvent event="click" listener={close} />
        &times;
      </button>
      <strong class="status">
        <Text property="status" />
      </strong>
      <Match>
        <Case predicate={hasText}>
          :
        </Case>
      </Match>
      <div class="message">
        <Text property="text" />
      </div>
    </div>;

    return [
      template(node, eventsBus),
      updateOnlyWhenStateChanges(({type}) => {
        const alert = Reference.getNode('alert');

        addClass(alert, `alert-${type}`);
      })
    ];
  };

  function hasText({text}) {
    return text;
  }

  function close({state: notification}) {
    removeNotification(notification);
  }
}
