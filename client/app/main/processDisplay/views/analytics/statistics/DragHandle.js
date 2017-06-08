import {jsx, createReferenceComponent, $document} from 'view-utils';
import {setHeight} from './service';

export function DragHandle() {
  return (parentNode, eventsBus) => {
    const Reference = createReferenceComponent();

    const template = <div className="drag-handle">
      <Reference name="handle" />
    </div>;

    const templateUpdate = template(parentNode, eventsBus);

    const handle = Reference.getNode('handle');

    let dragStart;
    let startHeight;

    const handleMouseUp = evt => {
      $document.removeEventListener('mousemove', handleMouseMove);
      $document.removeEventListener('mouseup', handleMouseUp);

      setHeight(startHeight - evt.screenY + dragStart);
    };

    const handleMouseMove = evt => {
      setHeight(startHeight - evt.screenY + dragStart);
    };

    handle.addEventListener('mousedown', evt => {
      evt.preventDefault();

      dragStart = evt.screenY;
      startHeight = parentNode.clientHeight;

      $document.addEventListener('mousemove', handleMouseMove);
      $document.addEventListener('mouseup', handleMouseUp);
    });

    return templateUpdate;
  };
}
