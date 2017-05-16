import {get} from 'http';
import {dispatchAction} from 'view-utils';
import {addNotification} from 'notifications';
import {interval} from 'utils';
import {createLoadingProgressAction, createLoadingProgressResultAction, createLoadingProgressErrorAction} from './reducer';

const REFRESH_TIME = 5000;

export function loadProgress() {
  dispatchAction(createLoadingProgressAction());

  const cancelTask = interval(progressTask, REFRESH_TIME);

  progressTask();

  function progressTask() {
    Promise
      .all([
        get('/api/status/import-progress').then(response => response.json()),
        get('/api/status/connection').then(response => response.json())
      ])
      .then(([importStatus, connectionStatus]) => {
        if (+importStatus.progress === 100) {
          cancelTask();
        }

        dispatchAction(createLoadingProgressResultAction({
          ...importStatus,
          ...connectionStatus
        }));
      })
      .catch(err => {
        cancelTask();
        addNotification({
          status: 'Could not load import progress',
          isError: true
        });
        dispatchAction(createLoadingProgressErrorAction(err));
      });
  }
}
