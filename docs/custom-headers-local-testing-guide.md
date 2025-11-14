# Custom Headers Local Testing Guide

This guide provides step-by-step instructions for testing the new custom headers functionality locally.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for local Elasticsearch/OpenSearch/PostgreSQL)
- Camunda 8 source code on branch `ad-extend-custom-headers-for-user-tasks`

## Part 1: Build and Start Camunda Locally

### 1. Build the Project

```bash
cd /path/to/camunda
mvn clean install -DskipTests -T 1C
```

### 2. Start Infrastructure (Elasticsearch + PostgreSQL)

```bash
docker-compose up -d elasticsearch postgres
```

### 3. Run Zeebe Broker

```bash
cd zeebe/broker
mvn exec:java
```

### 4. Run Tasklist (in another terminal)

```bash
cd tasklist/webapp
mvn spring-boot:run
```

## Part 2: Test FEEL Expressions in Custom Headers

### 1. Deploy Process with FEEL Expressions

Create a process file `test-headers-feel.bpmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
  <bpmn:process id="testHeadersFeel" isExecutable="true">
    <bpmn:startEvent id="start"/>
    <bpmn:userTask id="taskWithFeel" name="Task with FEEL Headers">
      <bpmn:extensionElements>
        <zeebe:taskHeaders>
          <zeebe:header key="department" value="engineering"/>
          <zeebe:header key="priority" value="=priorityVariable"/>
          <zeebe:header key="dueDate" value="=now() + duration(&quot;P3D&quot;)"/>
          <zeebe:header key="numericCalc" value="=baseValue + 10"/>
        </zeebe:taskHeaders>
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:endEvent id="end"/>
    <bpmn:sequenceFlow sourceRef="start" targetRef="taskWithFeel"/>
    <bpmn:sequenceFlow sourceRef="taskWithFeel" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>
```

Deploy it:

```bash
zbctl deploy test-headers-feel.bpmn
```

### 2. Start Process Instance with Variables

```bash
zbctl create instance testHeadersFeel \
  --variables '{"priorityVariable": "high", "baseValue": 5}'
```

### 3. Verify Expression Evaluation

Query the task via REST API:

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "bpmnProcessId": "testHeadersFeel"
    }
  }' | jq '.items[0].customHeaders'
```

**Expected Output**:
```json
{
  "department": "engineering",
  "priority": "high",
  "dueDate": "2025-11-17T10:00:00Z",
  "numericCalc": "15"
}
```

✅ Verify that:
- Static value `department` is preserved
- Expression `=priorityVariable` resolved to `"high"`
- FEEL function `=now() + duration("P3D")` calculated correctly
- Numeric expression `=baseValue + 10` converted to string `"15"`

## Part 3: Test Custom Header Filtering

### 1. Deploy Multiple Processes with Different Headers

```xml
<!-- Process 1: Engineering department -->
<zeebe:header key="department" value="engineering"/>
<zeebe:header key="region" value="EMEA"/>
<zeebe:header key="priority" value="high"/>

<!-- Process 2: Sales department -->
<zeebe:header key="department" value="sales"/>
<zeebe:header key="region" value="APAC"/>
<zeebe:header key="priority" value="medium"/>

<!-- Process 3: HR department -->
<zeebe:header key="department" value="hr"/>
<zeebe:header key="region" value="EMEA"/>
<zeebe:header key="priority" value="low"/>
```

Start instances of all three processes.

### 2. Test Filter by Exact Match ($eq)

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "department",
          "value": {"$eq": "engineering"}
        }
      ]
    }
  }' | jq '.items | length'
```

**Expected**: 1 task (only engineering)

### 3. Test Filter by Multiple Headers (AND logic)

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "region",
          "value": {"$eq": "EMEA"}
        },
        {
          "name": "priority",
          "value": {"$eq": "high"}
        }
      ]
    }
  }' | jq '.items[].customHeaders'
```

**Expected**: 1 task (engineering in EMEA with high priority)

### 4. Test Filter by Pattern Matching ($like)

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "department",
          "value": {"$like": "eng*"}
        }
      ]
    }
  }' | jq '.items | length'
```

**Expected**: 1 task (matches "engineering")

### 5. Test Filter by Existence ($exists)

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "priority",
          "value": {"$exists": true}
        }
      ]
    }
  }' | jq '.items | length'
```

**Expected**: 3 tasks (all have priority header)

### 6. Test Filter by Multiple Values ($in)

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "department",
          "value": {"$in": ["engineering", "hr"]}
        }
      ]
    }
  }' | jq '.items | length'
```

**Expected**: 2 tasks (engineering and hr)

### 7. Verify System Headers are NOT Searchable

```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        {
          "name": "io.camunda.zeebe:formKey",
          "value": {"$eq": "anyValue"}
        }
      ]
    }
  }' | jq '.items | length'
```

**Expected**: 0 tasks (system headers filtered out in Java code)

## Part 4: Test with Java Client API

```java
import io.camunda.zeebe.client.ZeebeClient;

public class CustomHeadersTest {
  public static void main(String[] args) {
    final ZeebeClient client = ZeebeClient.newClientBuilder()
        .gatewayAddress("localhost:26500")
        .usePlaintext()
        .build();

    // Search by custom headers
    final var result = client.newUserTaskSearchRequest()
        .filter(f -> f
            .customHeaders(List.of(
                h -> h.name("department").value("engineering"),
                h -> h.name("region").value(v -> v.like("EM*"))
            ))
        )
        .send()
        .join();

    System.out.println("Found " + result.items().size() + " tasks");
    result.items().forEach(task -> {
      System.out.println("Task: " + task.getUserTaskKey());
      System.out.println("Headers: " + task.getCustomHeaders());
    });
  }
}
```

## Part 5: Verify Database Storage

### Elasticsearch

```bash
curl -X GET "localhost:9200/tasklist-task-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": {
        "customHeaders.department": "engineering"
      }
    }
  }'
```

✅ Verify `customHeaders` is stored as a flattened field (not nested array)

### PostgreSQL

```sql
SELECT 
  key,
  custom_headers->>'department' as department,
  custom_headers->>'priority' as priority
FROM user_task
WHERE custom_headers IS NOT NULL;
```

✅ Verify `custom_headers` column contains JSON with evaluated values

## Troubleshooting

### Issue: Expressions Not Evaluated
- **Check**: Process deployed after code changes?
- **Fix**: Redeploy process definition

### Issue: Custom Headers Not Searchable
- **Check**: Index template applied?
- **Fix**: Delete indices and restart: `curl -X DELETE "localhost:9200/tasklist-task-*"`

### Issue: System Headers Appearing in Search
- **Check**: Using correct header name (no `io.camunda.zeebe:` prefix)?
- **Fix**: System headers are automatically filtered; use user-defined headers only

## Success Criteria

✅ FEEL expressions in header values are evaluated at task creation  
✅ Evaluated values are stored in all backends (RDBMS, ES, OS)  
✅ Custom header filtering works with all operators ($eq, $like, $exists, $in)  
✅ Multiple header filters use AND logic  
✅ System headers are excluded from search  
✅ Java client API works correctly  
✅ No dynamic fields in Elasticsearch schema  

## Additional Resources

- [Implementation Summary](./custom-headers-implementation-summary.md)
- [Camunda FEEL Documentation](https://docs.camunda.io/docs/components/modeler/feel/what-is-feel/)
- [User Task Search API](https://docs.camunda.io/docs/apis-tools/orchestration-cluster-api-rest/specifications/search-user-tasks/)
