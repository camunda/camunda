package org.camunda.es.scripts

reached = 0
all = 0

finalMap = new HashMap()
targetActivities = new ArrayList<>()
for (agg in _aggs) {
    if (agg["startActivity"] != null) {
        startActivity = agg["startActivity"]
    }
    if (agg["targetActivities"] != null && agg["targetActivities"].size() > 0) {
        targetActivities = agg["targetActivities"]
        break
    }
}

for (e in _aggs.process_activities) {
    for (pa in e) {
        if (!finalMap.containsKey(pa.key)) {
            finalMap.put(pa.key, new ArrayList())
        }

        for (act in pa.value) {
            finalMap.get(pa.key).add(act)
        }
    }
}

if (targetActivities != null && targetActivities.size() > 0) {
    for (pi in finalMap) {
        boolean allFound = true
        if (targetActivities.contains(startActivity)) {
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
resultMap['id'] = targetActivities.get(0)

return resultMap