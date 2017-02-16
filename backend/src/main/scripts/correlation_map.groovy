package org.camunda.es.scripts

def key = doc['processInstanceId'].value

if (!_agg['process_activities'].containsKey(key)) {
    _agg['process_activities'].put(key, new ArrayList())
}
_agg['process_activities'][key].add(doc["activityId"].value.toString())
_agg.targetActivities = _targetActivities
_agg.startActivity = _startActivity