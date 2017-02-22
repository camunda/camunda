import {jsx, DESTROY_EVENT, Children, createReferenceComponent, withSockets, $document, noop} from 'view-utils';
import $ from 'jquery';

export function createModal() {
  let modalNode;
  let ignoreListeners = false;

  const Modal = withSockets(({sockets: {head, body, foot}, onClose = noop}) => {
    const nodes = {};
    const Reference = createReferenceComponent(nodes);

    const template = <div className="modal fade" tabindex="-1" role="dialog">
      <Reference name="modal" />
      <div className="modal-dialog" role="document">
        <div className="modal-content">
          <div className="modal-header">
            <Children children={head} />
          </div>
          <div className="modal-body">
            <Children children={body} />
          </div>
          <div className="modal-footer">
            <Children children={foot} />
          </div>
        </div>
      </div>
    </div>;

    return (node, eventsBus) => {
      const templateUpdate = template($document.body, eventsBus);

      modalNode = $(nodes.modal);

      eventsBus.on(DESTROY_EVENT, () => {
        $document.body.removeChild(nodes.modal);
      });

      modalNode.modal({
        show: false
      });

      modalNode.on('hide.bs.modal', () => {
        if (!ignoreListeners) {
          onClose();
        }
      });

      return templateUpdate;
    };
  });

  Modal.open = () => {
    modalNode.modal('show');
  };

  Modal.close = (config = {}) => {
    ignoreListeners = config.ignoreListeners;
    modalNode.modal('hide');
    ignoreListeners = false;
  };

  return Modal;
}
