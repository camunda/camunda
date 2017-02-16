package org.camunda.es.scripts

def reached = 0
def all = 0

def finalMap = new HashMap()
def targetActivities = new ArrayList<>()

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
    for (pa in agg.process_activities) {
        if (!finalMap.containsKey(pa.key)) {
            finalMap.put(pa.key, new ArrayList())
        }

        for (act in pa.value) {
            finalMap.get(pa.key).add(act)
        }
    }
}

//iterate over process instances and do counts
if (targetActivities != null && targetActivities.size() > 0) {

    for (pi in finalMap) {
        boolean allFound = true
        if (pi.value.contains(startActivity)) {
            all = all + 1
        }
        for (act in targetActivities) {
            thisfound = false
            for (s in pi.value) {
                if (s.equalsIgnoreCase(act)) {
                    thisfound = true
                }
            }
            if (!thisfound) {
                allFound = false
                break
            }
        }
        if (allFound == true) {
            reached = reached + 1
        }
    }
}

def resultMap = [:]
resultMap['reached'] = reached
resultMap['all'] = all
resultMap['id'] = startActivity

return resultMap