#!/bin/bash
curl -s -X POST 'http://localhost:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d'
{
  "size": 0,
  "aggs": {
    "events": {
      "nested": {
        "path": "events"
      },
      "aggs": {
        "event_count": {
          "value_count": {
            "field": "events.id"
          }
        }
      }
    }
  }
}
' | jq '.aggregations.events.event_count.value // 0'