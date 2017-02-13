package org.camunda.es.scripts

key = ''
for (id in doc['processInstanceId'].values) {
    key = key + '-' + id
}
if (!_agg['process_activities'].containsKey(key)) {
    _agg['process_activities'].put(key, new ArrayList())
}
_agg['process_activities'][key].add(doc["activityId"].value.toString())
_agg.targetActivities = _targetActivities;
_agg.startActivity = _startActivity;