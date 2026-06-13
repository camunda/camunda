# Migration Guide: Test Case Generator Agent to team-qa-engineering

## Overview

This document provides options and instructions for moving the Test Case Generator Agent to the `camunda/team-qa-engineering` repository.

## Current Implementation

The Test Case Generator Agent is currently integrated as an MCP tool within the `camunda/camunda` repository at:

```
gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/
├── TestCaseGeneratorTools.java        (MCP tool interface)
├── GitHubProductHubService.java       (GitHub integration)
├── TestRailIntegrationService.java    (TestRail integration)
├── TestCaseTransformer.java           (transformation logic)
└── [tests, docs, configs]
```

**Dependencies:**
- Spring Boot MCP Server infrastructure
- Jackson for JSON processing
- Java HTTP client
- No Camunda-specific services

## Migration Options

### Option 1: Extract as Standalone MCP Server (RECOMMENDED)

Create an independent MCP server in `team-qa-engineering` that can be deployed separately.

#### Advantages
✅ Independent deployment and versioning  
✅ Faster iteration for QA team  
✅ Clear ownership  
✅ No Camunda build dependencies  
✅ Can be used by other teams/tools  

#### Implementation Steps

1. **Create new Spring Boot project in team-qa-engineering:**

```bash
# In team-qa-engineering repository
mkdir test-case-generator-agent
cd test-case-generator-agent

# Initialize with Spring Boot
spring init --dependencies=web,actuator --type=maven-project \
  --group=com.camunda.qa --artifact=test-case-generator-agent \
  --name="Test Case Generator Agent" .
```

2. **Add MCP dependencies to pom.xml:**

```xml
<dependencies>
    <!-- Spring AI MCP Server -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>mcp-annotations</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <dependency>
        <groupId>io.modelcontextprotocol.sdk</groupId>
        <artifactId>mcp-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Jackson for JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

3. **Copy source files:**

```bash
# Copy from camunda/camunda to team-qa-engineering
cp -r gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/* \
  test-case-generator-agent/src/main/java/com/camunda/qa/testgen/

# Copy tests
cp -r gateways/gateway-mcp/src/test/java/io/camunda/gateway/mcp/tool/testgen/* \
  test-case-generator-agent/src/test/java/com/camunda/qa/testgen/

# Copy documentation
cp gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/*.md \
  test-case-generator-agent/docs/

# Copy scripts
cp scripts/*testgen*.sh test-case-generator-agent/scripts/
```

4. **Update package names:**

Replace `io.camunda.gateway.mcp.tool.testgen` with `com.camunda.qa.testgen`

5. **Create application.yaml:**

```yaml
spring:
  application:
    name: Test Case Generator Agent
  ai:
    mcp:
      server:
        enabled: true
        name: Camunda Test Case Generator MCP Server
        version: '1.0.0'
        type: sync
        protocol: stateless
        annotation-scanner:
          enabled: true
        streamable-http:
          mcp-endpoint: /mcp
        capabilities:
          tool: true

server:
  port: 8081  # Different port from Camunda

camunda:
  qa:
    testgen:
      github:
        token: ${GITHUB_TOKEN:}
      testrail:
        url: ${TESTRAIL_URL}
        username: ${TESTRAIL_USERNAME}
        api-key: ${TESTRAIL_API_KEY}
```

6. **Create Dockerfile:**

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/test-case-generator-agent-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

7. **Create docker-compose.yaml for easy deployment:**

```yaml
version: '3.8'
services:
  test-case-generator:
    build: .
    ports:
      - "8081:8081"
    environment:
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - TESTRAIL_URL=${TESTRAIL_URL}
      - TESTRAIL_USERNAME=${TESTRAIL_USERNAME}
      - TESTRAIL_API_KEY=${TESTRAIL_API_KEY}
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

8. **Update scripts to point to new endpoint:**

In `setup-testgen-agent.sh` and `test-testgen-agent.sh`, change:
- `http://localhost:8080/mcp` → `http://localhost:8081/mcp`

#### Deployment

```bash
# Build
./mvnw clean package

# Run locally
java -jar target/test-case-generator-agent-*.jar

# Or with Docker
docker build -t camunda/test-case-generator-agent .
docker run -p 8081:8081 \
  -e GITHUB_TOKEN=$GITHUB_TOKEN \
  -e TESTRAIL_URL=$TESTRAIL_URL \
  -e TESTRAIL_USERNAME=$TESTRAIL_USERNAME \
  -e TESTRAIL_API_KEY=$TESTRAIL_API_KEY \
  camunda/test-case-generator-agent
```

---

### Option 2: Keep in camunda/camunda (Current State)

Keep the agent as part of the main Camunda distribution.

#### Advantages
✅ Already integrated  
✅ Part of Camunda distribution  
✅ Uses existing infrastructure  
✅ No migration needed  

#### Disadvantages
❌ Mixed concerns (product vs. QA tooling)  
❌ Requires full Camunda build  
❌ Slower iteration  

**Recommendation:** Keep here if the agent should be available to all Camunda users as part of the standard distribution.

---

### Option 3: Hybrid Approach

Keep a lightweight MCP tool wrapper in `camunda/camunda`, but move the core services to `team-qa-engineering` as a library.

#### Structure

**In camunda/camunda (gateway-mcp):**
```java
@Component
public class TestCaseGeneratorTools {
    private final TestGenAgentClient client;
    
    @McpTool(description = "...")
    public CallToolResult generateTestCasesFromEpic(...) {
        // Delegate to external service
        return client.generateTestCases(...);
    }
}
```

**In team-qa-engineering (library):**
```
test-case-generator-lib/
├── src/main/java/com/camunda/qa/testgen/
│   ├── GitHubProductHubService.java
│   ├── TestRailIntegrationService.java
│   └── TestCaseTransformer.java
└── pom.xml (published to Maven)
```

Then add dependency in camunda/camunda:
```xml
<dependency>
    <groupId>com.camunda.qa</groupId>
    <artifactId>test-case-generator-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Advantages
✅ MCP integration stays in main repo  
✅ Core logic owned by QA team  
✅ Can be reused by other tools  

#### Disadvantages
❌ More complex dependency management  
❌ Still requires Camunda build for MCP  

---

## Recommended Approach: Option 1 (Standalone MCP Server)

### Why This is Best

1. **Independence**: QA team has full control and can iterate quickly
2. **Clear Ownership**: Lives in team-qa-engineering where it logically belongs
3. **Reusability**: Can be deployed standalone or integrated elsewhere
4. **Simplicity**: No complex dependency chains
5. **Separation of Concerns**: Product code vs. QA tooling

### Migration Checklist

- [ ] Create new repository structure in team-qa-engineering
- [ ] Set up Spring Boot MCP server project
- [ ] Copy and adapt source files (update package names)
- [ ] Copy and update tests
- [ ] Copy documentation and scripts
- [ ] Set up CI/CD pipeline in team-qa-engineering
- [ ] Create Docker image
- [ ] Update user documentation with new endpoint
- [ ] Deploy to QA infrastructure
- [ ] Remove from camunda/camunda (optional - can keep both)

### Transition Period

You can keep both versions during transition:

1. **Standalone agent** in team-qa-engineering for QA team use
2. **Integrated version** in camunda/camunda for general availability

Eventually deprecate one based on usage patterns.

---

## Files to Extract

### Source Code
```
gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/
├── TestCaseGeneratorTools.java
├── GitHubProductHubService.java
├── TestRailIntegrationService.java
└── TestCaseTransformer.java
```

### Tests
```
gateways/gateway-mcp/src/test/java/io/camunda/gateway/mcp/tool/testgen/
├── TestCaseGeneratorToolsTest.java
└── TestCaseTransformerTest.java
```

### Documentation
```
gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/
├── README.md
├── USAGE_GUIDE.md
└── IMPLEMENTATION_SUMMARY.md
```

### Configuration & Scripts
```
gateways/gateway-mcp/src/main/resources/application-testgen-example.yaml
scripts/setup-testgen-agent.sh
scripts/test-testgen-agent.sh
```

### Optional
```
gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/
└── TestCaseGeneratorAgent.postman_collection.json
```

---

## Dependencies Analysis

The agent has **NO Camunda-specific dependencies**:

✅ Spring Boot - widely available  
✅ Jackson - standard JSON library  
✅ Java HTTP Client - built into JDK  
✅ Bean Validation - standard  

**This makes extraction very straightforward!**

---

## Testing the Extracted Version

```bash
# Build
./mvnw clean package

# Run
java -jar target/test-case-generator-agent-*.jar

# Test endpoint
curl http://localhost:8081/mcp

# Test tools
./scripts/test-testgen-agent.sh endpoint
```

---

## CI/CD for team-qa-engineering

### GitHub Actions Workflow

```yaml
name: Build and Test

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build with Maven
        run: ./mvnw clean package
      
      - name: Run tests
        run: ./mvnw test
      
      - name: Build Docker image
        run: docker build -t camunda/test-case-generator-agent .
      
      - name: Push to registry
        if: github.ref == 'refs/heads/main'
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker push camunda/test-case-generator-agent:latest
```

---

## Deployment Options

### 1. Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-case-generator
spec:
  replicas: 2
  selector:
    matchLabels:
      app: test-case-generator
  template:
    metadata:
      labels:
        app: test-case-generator
    spec:
      containers:
      - name: agent
        image: camunda/test-case-generator-agent:latest
        ports:
        - containerPort: 8081
        env:
        - name: GITHUB_TOKEN
          valueFrom:
            secretKeyRef:
              name: testgen-secrets
              key: github-token
        - name: TESTRAIL_URL
          value: "https://camunda.testrail.com"
        - name: TESTRAIL_USERNAME
          valueFrom:
            secretKeyRef:
              name: testgen-secrets
              key: testrail-username
        - name: TESTRAIL_API_KEY
          valueFrom:
            secretKeyRef:
              name: testgen-secrets
              key: testrail-api-key
---
apiVersion: v1
kind: Service
metadata:
  name: test-case-generator
spec:
  selector:
    app: test-case-generator
  ports:
  - port: 80
    targetPort: 8081
```

### 2. Docker Compose (Development)

Already provided above.

### 3. Standalone JAR

```bash
java -jar test-case-generator-agent.jar \
  --server.port=8081 \
  --camunda.qa.testgen.github.token=$GITHUB_TOKEN \
  --camunda.qa.testgen.testrail.url=$TESTRAIL_URL \
  --camunda.qa.testgen.testrail.username=$TESTRAIL_USERNAME \
  --camunda.qa.testgen.testrail.api-key=$TESTRAIL_API_KEY
```

---

## Comparison Summary

| Aspect | Keep in camunda/camunda | Standalone in team-qa-engineering |
|--------|------------------------|-----------------------------------|
| Ownership | Product team | QA team |
| Iteration Speed | Slow (full Camunda build) | Fast (independent) |
| Dependencies | Many (full Camunda) | Minimal (Spring Boot) |
| Deployment | Part of Camunda | Independent |
| Use Cases | All Camunda users | QA team specific |
| Maintenance | Product team concerns | QA team priorities |
| CI/CD | Camunda pipelines | QA team control |

---

## Conclusion

**Recommendation: Extract as Standalone MCP Server**

The Test Case Generator Agent is:
1. Primarily for QA team use
2. Has no Camunda-specific dependencies
3. Would benefit from independent iteration
4. Better suited for team-qa-engineering ownership

Follow Option 1 migration steps to extract it as a standalone service. The QA team can then:
- Deploy it independently
- Iterate quickly on QA-specific needs
- Use it with any MCP-compatible AI assistant
- Integrate it into QA workflows

The extraction is straightforward and will result in a cleaner separation of concerns.
