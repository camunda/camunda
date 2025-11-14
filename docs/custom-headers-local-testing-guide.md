# Local Testing Guide - Custom Headers Feature

## 📋 Prerequisites

- **Java**: JDK 21+
- **Maven**: 3.9+
- **Docker**: For running Elasticsearch/OpenSearch (optional)

```bash
java -version    # Should show Java 21+
mvn -version     # Should show Maven 3.9+
docker --version # Optional, for ES/OS testing
```

## 🚀 Quick Start - Test with H2 (In-Memory)

Fastest way to test without external dependencies.

### Build the Project
```bash
cd /Users/aleksander.dytko/Documents/GitHub/camunda
./mvnw clean install -DskipTests -T 1C
```

### Run Integration Tests
```bash
./mvnw test -pl qa/acceptance-tests -Dtest=UserTaskSearchTest
```

## 🧪 Manual Testing Steps

### 1. Deploy Process with FEEL Expressions

Create `test-custom-headers.bpmn`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                   xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
  <bpmn:process id="testCustomHeaders" isExecutable="true">
    <bpmn:startEvent id="start">
      <bpmn:outgoing>flow1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <bpmn:userTask id="taskStatic" name="Static Headers">
      <bpmn:extensionElements>
        <zeebe:taskHeaders>
          <zeebe:header key="department" value="engineering" />
          <zeebe:header key="priority" value="high" />
          <zeebe:header key="region" value="EMEA" />
        </zeebe:taskHeaders>
      </bpmn:extensionElements>
      <bpmn:incoming>flow1</bpmn:incoming>
      <bpmn:outgoing>flow2</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="taskFeel" name="FEEL Expressions">
      <bpmn:extensionElements>
        <zeebe:taskHeaders>
          <zeebe:header key="department" value="=deptVar" />
          <zeebe:header key="priority" value="=if urgency > 5 then &quot;high&quot; else &quot;low&quot;" />
          <zeebe:header key="score" value="=baseScore * 2" />
        </zeebe:taskHeaders>
      </bpmn:extensionElements>
      <bpmn:incoming>flow2</bpmn:incoming>
      <bpmn:outgoing>flow3</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:endEvent id="end">
      <bpmn:incoming>flow3</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="taskStatic" />
    <bpmn:sequenceFlow id="flow2" sourceRef="taskStatic" targetRef="taskFeel" />
    <bpmn:sequenceFlow id="flow3" sourceRef="taskFeel" targetRef="end" />
  </bpmn:process>
</bpmn:definitions>
```

### 2. Deploy via REST API
```bash
curl -X POST http://localhost:8080/v2/deployments \
  -H "Content-Type: multipart/form-data" \
  -F "resources=@test-custom-headers.bpmn"
```

### 3. Start Process Instance
```bash
curl -X POST http://localhost:8080/v2/process-instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionKey": "<KEY>",
    "variables": {
      "deptVar": "sales",
      "urgency": 8,
      "baseScore": 25
    }
  }'
```

### 4. Search Tests

#### Exact Match
```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        { "name": "department", "value": { "$eq": "engineering" } }
      ]
    }
  }' | jq
```

#### Multiple Headers (AND)
```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        { "name": "department", "value": { "$eq": "engineering" } },
        { "name": "region", "value": { "$eq": "EMEA" } }
      ]
    }
  }' | jq
```

#### LIKE Pattern
```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        { "name": "department", "value": { "$like": "engi*" } }
      ]
    }
  }' | jq
```

#### EXISTS
```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        { "name": "priority", "value": { "$exists": true } }
      ]
    }
  }' | jq
```

#### Verify System Headers NOT Searchable
```bash
curl -X POST http://localhost:8080/v2/user-tasks/search \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {
      "customHeaders": [
        { "name": "io.camunda.zeebe:formKey", "value": { "$eq": "any" } }
      ]
    }
  }' | jq
```

Expected: No results (system headers filtered out)

### 5. Verify Evaluated FEEL Expressions

```bash
curl http://localhost:8080/v2/user-tasks/<KEY> | jq '.customHeaders'
```

Expected:
```json
{
  "department": "sales",
  "priority": "high",
  "score": "50"
}
```

## 🧪 Java Client Testing

```java
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.Map;
import java.util.List;

public class CustomHeadersTest {
  public static void main(String[] args) throws Exception {
    CamundaClient client = CamundaClient.newClient();
    
    // Deploy
    var deployment = client.newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("test")
                .startEvent()
                .userTask("task1")
                .zeebeUserTask()
                .zeebeTaskHeader("dept", "engineering")
                .zeebeTaskHeader("priority", "=basePriority + 1")
                .endEvent()
                .done(),
            "test.bpmn")
        .send().join();
    
    // Start instance
    client.newCreateInstanceCommand()
        .bpmnProcessId("test")
        .latestVersion()
        .variable("basePriority", 5)
        .send().join();
    
    Thread.sleep(2000); // Wait for export
    
    // Search by exact match
    var result = client.newUserTaskSearchRequest()
        .filter(f -> f.customHeaders(Map.of("dept", "engineering")))
        .send().join();
    
    System.out.println("Found: " + result.items().size());
    
    // Search with LIKE
    result = client.newUserTaskSearchRequest()
        .filter(f -> f.customHeaders(List.of(
            h -> h.name("dept").value(v -> v.like("engi*"))
        )))
        .send().join();
    
    System.out.println("Found with LIKE: " + result.items().size());
    
    // Print custom headers
    if (!result.items().isEmpty()) {
      result.items().get(0).getCustomHeaders()
          .forEach((k, v) -> System.out.println(k + " = " + v));
    }
    
    client.close();
  }
}
```

## ✅ Verification Checklist

- [ ] Process deploys with custom headers
- [ ] Static headers show correct values
- [ ] FEEL expressions show evaluated values
- [ ] Search by exact match works
- [ ] Search by multiple headers (AND) works
- [ ] Search with LIKE works
- [ ] Search with EXISTS works
- [ ] System headers NOT searchable
- [ ] Custom headers visible in task response

## 🐛 Troubleshooting

### Tests Skip
**Issue**: Integration tests show "Tests are skipped"  
**Solution**: Tests require full Camunda cluster. Use manual testing.

### Connection Refused
**Issue**: `Connection refused to localhost:26500`  
**Solution**: Start broker with `./bin/camunda start`

### Custom Headers Not Searchable
**Possible Causes**:
1. Task not exported yet - wait a few seconds
2. Index not updated - check ES template
3. System header - they're not searchable

**Debug**:
```bash
# Check if task in Elasticsearch
curl http://localhost:9200/tasklist-task-*/_search | jq

# Verify mapping
curl http://localhost:9200/tasklist-task-*/_mapping | \
  jq '.[] | .mappings.properties.customHeaders'
```

### FEEL Expression Fails
**Solution**: Check expression syntax and variables:
```bash
curl http://localhost:8080/v2/process-instances/<KEY> | jq '.variables'
```

## 🔍 Success Criteria

✅ All these should work:
1. Process deploys with static and FEEL headers
2. Static headers show original values
3. FEEL expressions show computed values
4. All search operators work
5. Multiple header filters work (AND)
6. System headers excluded from search
7. Performance acceptable (< 100ms)

---

**Questions?** See implementation summary for architecture details.

