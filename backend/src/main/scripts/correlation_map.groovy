package org.camunda.es.scripts

def processInstanceId = doc['processInstanceId'].value

if (!_agg['process_activities'].containsKey(processInstanceId)) {
  _agg['process_activities'].put(processInstanceId, new ArrayList())
}
_agg['process_activities'][processInstanceId].add(doc["activityId"].value.toString())
_agg.targetActivities = _targetActivities
_agg.startActivity = _startActivity