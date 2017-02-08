import {jsx, DESTROY_EVENT, Children, createReferenceComponent, withSockets, $document} from 'view-utils';
import $ from 'jquery';

export const Modal = withSockets(({open, onClose, sockets: {head, body, foot}}) => {
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
    const modalNode = $(nodes.modal);

    eventsBus.on(DESTROY_EVENT, () => {
      $document.body.removeChild(nodes.modal);
    });

    modalNode.modal({
      show: false
    });
    modalNode.on('hide.bs.modal', onClose);

    return [templateUpdate, (state) => {
      modalNode.modal(open(state) ? 'show' : 'hide');
    }];
  };
});
