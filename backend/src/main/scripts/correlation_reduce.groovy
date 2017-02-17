package org.camunda.es.scripts

boolean checkProcessInstanceForActivities(targetActivities, processInstanceEntry) {
  boolean allTargetsFound = true
  for (targetActivity in targetActivities) {
    thisTargetFound = false
    for (processInstanceActivity in processInstanceEntry.value) {
      if (processInstanceActivity.equalsIgnoreCase(targetActivity)) {
        thisTargetFound = true
        break
      }
    }
    if (!thisTargetFound) {
      allTargetsFound = false
      break
    }
  }
  return allTargetsFound
}

def allTargetActivitiesReached = 0
def startActivityCount = 0

def finalMap = new HashMap()
def targetActivities = new ArrayList<>()
def startActivity = null

//grab filtering criteria out of any agg, they are all same
for (agg in _aggs) {
  if (agg["startActivity"] != null) {
    startActivity = agg["startActivity"]
  }
  if (agg["targetActivities"] != null && agg["targetActivities"].size() > 0) {
    targetActivities = agg["targetActivities"]
    break
  }
}

//build a map of process instances with all activities that they passed
for (agg in _aggs) {
  for (processActivitiesEntry in agg.process_activities) {
    if (!finalMap.containsKey(processActivitiesEntry.key)) {
      finalMap.put(processActivitiesEntry.key, new ArrayList())
    }

    for (activityId in processActivitiesEntry.value) {
      finalMap.get(processActivitiesEntry.key).add(activityId)
    }
  }
}



//iterate over process instances and do counts, but only if there are any mapping results available
if (targetActivities != null && targetActivities.size() > 0 && startActivity != null) {

  for (processInstanceEntry in finalMap) {
    if (processInstanceEntry.value.contains(startActivity)) {
      startActivityCount = startActivityCount + 1
    }
    def allTargetsFound = checkProcessInstanceForActivities(targetActivities, processInstanceEntry)

    if (allTargetsFound == true) {
      allTargetActivitiesReached = allTargetActivitiesReached + 1
    }
  }
}

def resultMap = [:]
resultMap['activitiesReached'] = allTargetActivitiesReached
resultMap['startActivityCount'] = startActivityCount
resultMap['startActivityId'] = startActivity

return resultMap