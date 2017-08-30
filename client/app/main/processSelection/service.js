import {dispatchAction} from 'view-utils';
import {get} from 'http';
import {
  createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction,
  createLoadProcessDefinitionsErrorAction, createSetVersionAction,
  createSetVersionXmlAction
} from './reducer';
import {getRouter} from 'router';
import {addNotification} from 'notifications';

const router = getRouter();

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition/groupedByKey')
    .then(response => response.json())
    .then(result => {
      const withLastestVersions = result.map(({versions}) => {
        const latestVersion = getLatestVersion(versions);

        return {
          current: latestVersion,
          versions
        };
      });
      const ids = withLastestVersions
        .map(({current: {id}}) => id)
        .reduce((ids, id) => {
          if (ids.length) {
            return ids + '&ids=' + encodeURIComponent(id);
          }

          return 'ids=' + encodeURIComponent(id);
        }, '');

      return get('/api/process-definition/xml?' + ids)
        .then(response => response.json())
        .then(xmls => {
          return withLastestVersions.map(entry => {
            const xml = xmls[entry.current.id];

            entry.current.bpmn20Xml = xml;

            return entry;
          });
        });
    })
    .then(result => {
      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load process definitions',
        isError: true
      });
      dispatchAction(createLoadProcessDefinitionsErrorAction(err));
    });
}

function getLatestVersion(versions) {
  return versions.sort(({version: versionA}, {version: versionB}) => {
    return versionB - versionA;
  })[0];
}

export function openDefinition(id) {
  router.goTo('processDisplay', {definition: id});
}

export function setVersionForProcess({id, key, version, bpmn20Xml}) {
  if (typeof bpmn20Xml !== 'string') {
    return get(`/api/process-definition/${id}/xml`)
      .then(response => response.text())
      .then(xml => {
        dispatchAction(createSetVersionXmlAction(key, version, xml));
        dispatchAction(createSetVersionAction(key, version));
      });
  }

  dispatchAction(createSetVersionAction(key, version));
}
